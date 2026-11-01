import CoreMedia
import CoreVideo
import FFmpeg
import Foundation

struct EngineError: Error, CustomStringConvertible {
    let description: String

    init(_ what: String, _ code: Int32? = nil) {
        guard let code else {
            description = what
            return
        }
        var text = [CChar](repeating: 0, count: 128)
        av_strerror(code, &text, text.count)
        description = "\(what): \(String(cString: text)) (\(code))"
    }
}

/// One track of a file the proxy serves, demuxed and decoded by FFmpeg (vp9, libdav1d, opus or aac) and handed back
/// as sample buffers AVFoundation's renderers take: video as NV12 or P010 pixel buffers, sound as interleaved float.
/// Used from one queue at a time.
final class TrackDecoder {
    enum Kind: String {
        case video, audio
    }

    let kind: Kind
    private let reader: RangeReader
    private(set) var decoderName = ""
    /// The container's duration, 0 when it does not say.
    private(set) var durationSeconds = 0.0
    /// Frames passed over while skipping to a keyframe, taken by the engine as dropped.
    private(set) var skippedFrames = 0
    private(set) var pictureSize: String?
    /** What the last picture was handed over as: the pixel format, and the colour attached to it. */
    private(set) var pixelFormatName: String?
    private(set) var colourPrimariesName: String?
    private(set) var transferFunctionName: String?

    private var io: UnsafeMutablePointer<AVIOContext>?
    private var format: UnsafeMutablePointer<AVFormatContext>?
    private var codec: UnsafeMutablePointer<AVCodecContext>?
    private var packet = av_packet_alloc()
    private var frame = av_frame_alloc()
    private var streamIndex: Int32 = -1
    private var timeBase = AVRational(num: 1, den: 1000)
    private var draining = false
    private var skippingToKeyframe = false
    private var failedPackets = 0
    private var lastTime = CMTime.invalid

    private var converter: UnsafeMutableRawPointer?
    private var pool: CVPixelBufferPool?
    private var poolShape = (0, 0, OSType(0))
    private var videoFormat: CMVideoFormatDescription?

    private var resampler: OpaquePointer?
    private var resamplerShape = (Int32(0), Int32(0), Int32(0))
    private var audioFormat: CMAudioFormatDescription?
    private var pcm: [Float] = []

    init(url: URL, kind: Kind) throws {
        self.kind = kind
        reader = RangeReader(url: url)
        let bufferSize: Int32 = 1 << 16
        guard let buffer = av_malloc(Int(bufferSize))?.assumingMemoryBound(to: UInt8.self) else { throw EngineError("no memory") }
        io = avio_alloc_context(buffer, bufferSize, 0, Unmanaged.passUnretained(reader).toOpaque(), rangeReaderRead, nil, rangeReaderSeek)
        guard let io else {
            av_free(buffer)
            throw EngineError("no memory")
        }
        var opening: UnsafeMutablePointer<AVFormatContext>? = avformat_alloc_context()
        opening?.pointee.pb = io
        opening?.pointee.flags |= AVFMT_FLAG_CUSTOM_IO
        // On failure FFmpeg frees the context it was given, but not our I/O, which deinit frees.
        let opened = avformat_open_input(&opening, url.lastPathComponent, nil, nil)
        guard opened >= 0, let context = opening else { throw EngineError("open \(url.lastPathComponent) (\(reader.error ?? "no read error"))", opened) }
        format = context

        let index = av_find_best_stream(context, kind == .video ? AVMEDIA_TYPE_VIDEO : AVMEDIA_TYPE_AUDIO, -1, -1, nil, 0)
        guard index >= 0 else { throw EngineError("no \(kind.rawValue) stream in \(url.lastPathComponent)", index) }
        streamIndex = index
        for other in 0..<Int(context.pointee.nb_streams) where other != Int(index) {
            context.pointee.streams[other]?.pointee.discard = AVDISCARD_ALL
        }
        guard let stream = context.pointee.streams[Int(index)], let parameters = stream.pointee.codecpar else {
            throw EngineError("no \(kind.rawValue) stream in \(url.lastPathComponent)")
        }
        timeBase = stream.pointee.time_base
        if context.pointee.duration > 0 { durationSeconds = Double(context.pointee.duration) / Double(AV_TIME_BASE) }

        guard let decoder = avcodec_find_decoder(parameters.pointee.codec_id) else {
            throw EngineError("no decoder built for \(String(cString: avcodec_get_name(parameters.pointee.codec_id)))")
        }
        codec = avcodec_alloc_context3(decoder)
        guard let codec else { throw EngineError("no memory") }
        let copied = avcodec_parameters_to_context(codec, parameters)
        guard copied >= 0 else { throw EngineError("codec parameters", copied) }
        // Lets FFmpeg move timestamps past the samples it trims (Opus pre-skip, AAC priming).
        codec.pointee.pkt_timebase = stream.pointee.time_base
        // One thread per core: VP9 and dav1d decode several frames at once.
        codec.pointee.thread_count = 0
        let ready = avcodec_open2(codec, decoder, nil)
        guard ready >= 0 else { throw EngineError("open \(String(cString: decoder.pointee.name))", ready) }
        decoderName = String(cString: decoder.pointee.name)
    }

    deinit {
        reader.cancel()
        flow_free_converter(converter)
        swr_free(&resampler)
        avcodec_free_context(&codec)
        avformat_close_input(&format)
        if io != nil {
            av_free(io?.pointee.buffer)
            avio_context_free(&io)
        }
        av_frame_free(&frame)
        av_packet_free(&packet)
    }

    /// Ends a read blocked on the network, from any thread; the decoder is not used afterwards.
    func cancel() {
        reader.cancel()
    }

    /// Takes the frames passed over since the last call.
    func takeSkippedFrames() -> Int {
        defer { skippedFrames = 0 }
        return skippedFrames
    }

    /// The next frame as a sample buffer, nil at the end of the track.
    func next() throws -> CMSampleBuffer? {
        while true {
            let received = avcodec_receive_frame(codec, frame)
            if received == 0 {
                defer { av_frame_unref(frame) }
                if let sample = try kind == .video ? videoSample() : audioSample() { return sample }
                continue
            }
            if received == flow_averror_eof() { return nil }
            guard received == flow_averror_eagain(), !draining else { throw EngineError("decode \(decoderName)", received) }
            try feed()
        }
    }

    /// Lands on the keyframe at or before [time]; the engine discards what decodes before [time].
    func seek(to time: CMTime) {
        let microseconds = Int64(CMTimeGetSeconds(time) * 1_000_000)
        let timestamp = av_rescale_q(microseconds, AVRational(num: 1, den: 1_000_000), timeBase)
        av_seek_frame(format, streamIndex, timestamp, AVSEEK_FLAG_BACKWARD)
        avcodec_flush_buffers(codec)
        draining = false
        skippingToKeyframe = false
        lastTime = .invalid
    }

    /// When decoding has fallen too far behind the clock: the frames up to the next keyframe are not decoded at all.
    func skipToNextKeyframe() {
        skippingToKeyframe = true
    }

    private func feed() throws {
        while true {
            let read = av_read_frame(format, packet)
            if read == flow_averror_eof() {
                draining = true
                avcodec_send_packet(codec, nil)
                return
            }
            guard read >= 0 else { throw EngineError("read \(kind.rawValue)" + (reader.error.map { " (\($0))" } ?? ""), read) }
            defer { av_packet_unref(packet) }
            guard packet?.pointee.stream_index == streamIndex else { continue }
            if skippingToKeyframe {
                guard flow_is_keyframe(packet) != 0 else {
                    skippedFrames += 1
                    continue
                }
                skippingToKeyframe = false
                avcodec_flush_buffers(codec)
            }
            let sent = avcodec_send_packet(codec, packet)
            if sent >= 0 {
                failedPackets = 0
                return
            }
            // A damaged packet is passed over, as FFmpeg's own players do; a decoder that takes none is an error.
            failedPackets += 1
            if failedPackets > 50 { throw EngineError("decode \(decoderName)", sent) }
        }
    }

    private func presentationTime(duration: Int64) -> (time: CMTime, duration: CMTime) {
        let raw = frame!.pointee.best_effort_timestamp
        let length = CMTime(value: duration * Int64(timeBase.num), timescale: timeBase.den)
        let time = raw == flow_nopts() ? (lastTime.isValid ? lastTime : .zero) : CMTime(value: raw * Int64(timeBase.num), timescale: timeBase.den)
        lastTime = duration > 0 ? CMTimeAdd(time, length) : time
        return (time, duration > 0 ? length : .invalid)
    }

    private func videoSample() throws -> CMSampleBuffer? {
        let picture = frame!.pointee
        let width = Int(picture.width), height = Int(picture.height)
        let deep = flow_pixel_depth(picture.format) > 8
        let full = picture.color_range == AVCOL_RANGE_JPEG
        let pixelFormat = deep
            ? (full ? kCVPixelFormatType_420YpCbCr10BiPlanarFullRange : kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange)
            : (full ? kCVPixelFormatType_420YpCbCr8BiPlanarFullRange : kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange)
        if pool == nil || poolShape != (width, height, pixelFormat) {
            let attributes: [String: Any] = [
                kCVPixelBufferPixelFormatTypeKey as String: pixelFormat,
                kCVPixelBufferWidthKey as String: width,
                kCVPixelBufferHeightKey as String: height,
                kCVPixelBufferIOSurfacePropertiesKey as String: [String: Any](),
            ]
            pool = nil
            CVPixelBufferPoolCreate(nil, nil, attributes as CFDictionary, &pool)
            poolShape = (width, height, pixelFormat)
            pictureSize = "\(width)x\(height)"
            pixelFormatName = (deep ? "P010" : "NV12") + (full ? " full range" : "")
        }
        var created: CVPixelBuffer?
        guard let pool, CVPixelBufferPoolCreatePixelBuffer(nil, pool, &created) == kCVReturnSuccess, let pixels = created else {
            throw EngineError("no pixel buffer for \(width)x\(height)")
        }
        converter = flow_biplanar_converter(converter, picture.width, picture.height, picture.format, deep ? 1 : 0)
        guard let converter else { throw EngineError("no converter from pixel format \(picture.format)") }
        CVPixelBufferLockBaseAddress(pixels, [])
        let converted = flow_convert_to_biplanar(
            converter, frame,
            CVPixelBufferGetBaseAddressOfPlane(pixels, 0)?.assumingMemoryBound(to: UInt8.self), Int32(CVPixelBufferGetBytesPerRowOfPlane(pixels, 0)),
            CVPixelBufferGetBaseAddressOfPlane(pixels, 1)?.assumingMemoryBound(to: UInt8.self), Int32(CVPixelBufferGetBytesPerRowOfPlane(pixels, 1))
        )
        CVPixelBufferUnlockBaseAddress(pixels, [])
        guard converted > 0 else { throw EngineError("convert picture", converted) }
        attachColour(to: pixels, picture)

        if videoFormat == nil || !CMVideoFormatDescriptionMatchesImageBuffer(videoFormat!, imageBuffer: pixels) {
            videoFormat = nil
            CMVideoFormatDescriptionCreateForImageBuffer(allocator: nil, imageBuffer: pixels, formatDescriptionOut: &videoFormat)
        }
        guard let videoFormat else { throw EngineError("no video format description") }
        let (time, duration) = presentationTime(duration: picture.duration)
        var timing = CMSampleTimingInfo(duration: duration, presentationTimeStamp: time, decodeTimeStamp: .invalid)
        var sample: CMSampleBuffer?
        CMSampleBufferCreateReadyWithImageBuffer(allocator: nil, imageBuffer: pixels, formatDescription: videoFormat, sampleTiming: &timing, sampleBufferOut: &sample)
        return sample
    }

    // The picture's colour as the container or bitstream states it; unstated, HD is BT.709 and SD BT.601, as players assume.
    private func attachColour(to pixels: CVPixelBuffer, _ picture: AVFrame) {
        let hd = picture.height >= 720
        let primaries: CFString
        switch picture.color_primaries {
        case AVCOL_PRI_BT2020: primaries = kCVImageBufferColorPrimaries_ITU_R_2020
        case AVCOL_PRI_BT470BG: primaries = kCVImageBufferColorPrimaries_EBU_3213
        case AVCOL_PRI_SMPTE170M, AVCOL_PRI_SMPTE240M: primaries = kCVImageBufferColorPrimaries_SMPTE_C
        case AVCOL_PRI_BT709: primaries = kCVImageBufferColorPrimaries_ITU_R_709_2
        default: primaries = hd ? kCVImageBufferColorPrimaries_ITU_R_709_2 : kCVImageBufferColorPrimaries_SMPTE_C
        }
        let transfer: CFString
        switch picture.color_trc {
        case AVCOL_TRC_SMPTE2084: transfer = kCVImageBufferTransferFunction_SMPTE_ST_2084_PQ
        case AVCOL_TRC_ARIB_STD_B67: transfer = kCVImageBufferTransferFunction_ITU_R_2100_HLG
        default: transfer = kCVImageBufferTransferFunction_ITU_R_709_2
        }
        let matrix: CFString
        switch picture.colorspace {
        case AVCOL_SPC_BT2020_NCL, AVCOL_SPC_BT2020_CL: matrix = kCVImageBufferYCbCrMatrix_ITU_R_2020
        case AVCOL_SPC_BT470BG, AVCOL_SPC_SMPTE170M: matrix = kCVImageBufferYCbCrMatrix_ITU_R_601_4
        case AVCOL_SPC_BT709: matrix = kCVImageBufferYCbCrMatrix_ITU_R_709_2
        default: matrix = hd ? kCVImageBufferYCbCrMatrix_ITU_R_709_2 : kCVImageBufferYCbCrMatrix_ITU_R_601_4
        }
        colourPrimariesName = primaries as String
        transferFunctionName = transfer as String
        CVBufferSetAttachment(pixels, kCVImageBufferColorPrimariesKey, primaries, .shouldPropagate)
        CVBufferSetAttachment(pixels, kCVImageBufferTransferFunctionKey, transfer, .shouldPropagate)
        CVBufferSetAttachment(pixels, kCVImageBufferYCbCrMatrixKey, matrix, .shouldPropagate)
    }

    private func audioSample() throws -> CMSampleBuffer? {
        let sound = frame!.pointee
        // Surround is mixed down to stereo; mono stays mono.
        let channels = min(max(sound.ch_layout.nb_channels, 1), 2)
        if resampler == nil || resamplerShape != (sound.sample_rate, channels, sound.format) {
            swr_free(&resampler)
            let ready = flow_resampler(&resampler, frame, channels)
            guard ready >= 0 else { throw EngineError("resampler", ready) }
            resamplerShape = (sound.sample_rate, channels, sound.format)
            audioFormat = nil
        }
        let capacity = Int(sound.nb_samples) + 256
        if pcm.count < capacity * Int(channels) { pcm = [Float](repeating: 0, count: capacity * Int(channels)) }
        let count = pcm.withUnsafeMutableBufferPointer { flow_resample(resampler, frame, $0.baseAddress, Int32(capacity)) }
        guard count >= 0 else { throw EngineError("resample", count) }
        if count == 0 { return nil }

        if audioFormat == nil {
            var description = AudioStreamBasicDescription(
                mSampleRate: Float64(sound.sample_rate),
                mFormatID: kAudioFormatLinearPCM,
                mFormatFlags: kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked,
                mBytesPerPacket: UInt32(4 * channels),
                mFramesPerPacket: 1,
                mBytesPerFrame: UInt32(4 * channels),
                mChannelsPerFrame: UInt32(channels),
                mBitsPerChannel: 32,
                mReserved: 0
            )
            CMAudioFormatDescriptionCreate(
                allocator: kCFAllocatorDefault, asbd: &description, layoutSize: 0, layout: nil,
                magicCookieSize: 0, magicCookie: nil, extensions: nil, formatDescriptionOut: &audioFormat
            )
        }
        guard let audioFormat else { throw EngineError("no audio format description") }
        let bytes = Int(count) * Int(channels) * MemoryLayout<Float>.size
        var created: CMBlockBuffer?
        CMBlockBufferCreateWithMemoryBlock(
            allocator: kCFAllocatorDefault, memoryBlock: nil, blockLength: bytes, blockAllocator: kCFAllocatorDefault,
            customBlockSource: nil, offsetToData: 0, dataLength: bytes, flags: kCMBlockBufferAssureMemoryNowFlag, blockBufferOut: &created
        )
        guard let block = created else { throw EngineError("no memory") }
        pcm.withUnsafeBytes { _ = CMBlockBufferReplaceDataBytes(with: $0.baseAddress!, blockBuffer: block, offsetIntoDestination: 0, dataLength: bytes) }
        let (time, _) = presentationTime(duration: sound.duration)
        var sample: CMSampleBuffer?
        CMAudioSampleBufferCreateReadyWithPacketDescriptions(
            allocator: kCFAllocatorDefault, dataBuffer: block, formatDescription: audioFormat, sampleCount: CMItemCount(count),
            presentationTimeStamp: time, packetDescriptions: nil, sampleBufferOut: &sample
        )
        return sample
    }
}
