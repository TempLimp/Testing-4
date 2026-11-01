import AVFoundation
import AVKit
import Shared

/// The bundled engine (PORTING_PLAN line 20, S04b): VP9, AV1 without a hardware decoder and Opus, which AVPlayer cannot
/// play, decoded by FFmpeg and dav1d and rendered by AVSampleBufferDisplayLayer and AVSampleBufferAudioRenderer under one
/// AVSampleBufferRenderSynchronizer, whose clock follows the sound. It reads the files LocalHlsProxy serves and sits
/// behind the same PlaybackEngine contract as AVPlayer; Kotlin opens it through PlaybackEngines.
///
/// A/V sync: every frame is shown at its own timestamp on the synchronizer's clock. A frame decoded more than
/// `lateTolerance` past the clock is dropped rather than shown late, so a shown frame is at most that far from the
/// sound; one more than `catchUpAfter` behind skips decoding to the next keyframe, as ExoPlayer does. After opening and
/// after a seek the clock stays parked until the first frame at or after its target is on the display (the sound alone
/// would start it sooner, and that frame would then be late).
final class BundledEngine: NSObject, PlaybackEngine {
    static let lateTolerance = CMTime(value: 40, timescale: 1000)
    static let catchUpAfter = CMTime(value: 1, timescale: 1)

    private let displayLayer = AVSampleBufferDisplayLayer()
    private let audioRenderer = AVSampleBufferAudioRenderer()
    private let synchronizer = AVSampleBufferRenderSynchronizer()
    private let control = DispatchQueue(label: "flow.engine.control", qos: .userInitiated)
    private let videoQueue = DispatchQueue(label: "flow.engine.video", qos: .userInitiated)
    private let audioQueue = DispatchQueue(label: "flow.engine.audio", qos: .userInitiated)
    private let lock = NSLock()

    /// Decoded frames and sound, before they are queued (tests check timing; S12's equalizer and visualizer tap the PCM).
    var videoTap: ((CVPixelBuffer, CMTime) -> Void)?
    var audioTap: ((UnsafeBufferPointer<Float>, Int, Double, CMTime) -> Void)?

    // Guarded by lock.
    private var video: TrackDecoder?
    private var audio: TrackDecoder?
    private var generation = 0
    private var opened = false
    private var closed = false
    private var wantsToPlay = false
    private var seekTarget = CMTime.zero
    private var startAt = CMTime.zero
    private var clockParked = false
    private var videoEnded = false
    private var audioEnded = false
    private var audioQueuedUntil = CMTime.invalid
    private var audioStarving = false
    private var failure: String?
    private var duration: Int64 = 0
    private var framesDecoded = 0
    private var framesRendered = 0
    private var framesDropped = 0
    private var maxLateMs = 0.0
    private var audioQueuedMs = 0.0
    private var underruns = 0
    private var seekLandedMs: Int64 = -1
    private var pictureSize: String?
    private var pixelFormat: String?
    private var colorPrimaries: String?
    private var transferFunction: String?

    private var pictureInPicture: AVPictureInPictureController?
    private var watchdog: DispatchSourceTimer?
    private var flushObserver: NSObjectProtocol?

    override init() {
        super.init()
        displayLayer.videoGravity = .resizeAspect
    }

    deinit {
        watchdog?.cancel()
        if let flushObserver { NotificationCenter.default.removeObserver(flushObserver) }
    }

    // MARK: PlaybackEngine

    var layer: CALayer { displayLayer }

    func prepare(source: MediaSource) {
        let hasVideo = source.mimeType?.hasPrefix("video/") == true
        open(
            video: hasVideo ? URL(string: source.uri) : nil,
            audio: (hasVideo ? source.audioUri : source.uri).flatMap { URL(string: $0) },
            durationMs: source.durationMs
        )
    }

    func play() {
        lock.lock()
        wantsToPlay = true
        let ready = opened && !closed && !clockParked
        lock.unlock()
        if ready { control.async { self.synchronizer.rate = 1 } }
        invalidatePictureInPicture()
    }

    func pause() {
        lock.lock()
        wantsToPlay = false
        let ready = opened && !closed
        lock.unlock()
        if ready { control.async { self.synchronizer.rate = 0 } }
        invalidatePictureInPicture()
    }

    func seekTo(positionMs: Int64) {
        lock.lock()
        // The feeds running now stop here, so none of their frames is taken for where the seek landed. Before the
        // files are open, playback will start there instead.
        seekLandedMs = -1
        guard opened else {
            startAt = CMTime(value: positionMs, timescale: 1000)
            lock.unlock()
            return
        }
        generation += 1
        lock.unlock()
        control.async { self.seek(to: CMTime(value: positionMs, timescale: 1000)) }
    }

    func currentPositionMs() -> Int64 {
        let seconds = CMTimeGetSeconds(synchronizer.currentTime())
        return seconds.isFinite ? Int64(seconds * 1000) : 0
    }

    func durationMs() -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        return duration
    }

    func isPlaying() -> Bool {
        synchronizer.rate > 0
    }

    func error() -> String? {
        lock.lock()
        defer { lock.unlock() }
        return failure
    }

    func stats() -> PlaybackStats {
        lock.lock()
        defer { lock.unlock() }
        return PlaybackStats(
            videoDecoder: video?.decoderName,
            audioDecoder: audio?.decoderName,
            videoSize: pictureSize,
            framesDecoded: Int32(framesDecoded),
            framesRendered: Int32(framesRendered),
            framesDropped: Int32(framesDropped),
            maxVideoLateMs: maxLateMs,
            audioQueuedMs: Int64(audioQueuedMs),
            audioRendering: audio != nil && audioRenderer.status == .rendering && audioRenderer.error == nil,
            audioUnderruns: Int32(underruns),
            seekLandedMs: seekLandedMs,
            pixelFormat: pixelFormat,
            colorPrimaries: colorPrimaries,
            transferFunction: transferFunction
        )
    }

    func pictureInPictureController() -> AVPictureInPictureController? {
        if let pictureInPicture { return pictureInPicture }
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return nil }
        let controller = AVPictureInPictureController(contentSource: .init(sampleBufferDisplayLayer: displayLayer, playbackDelegate: self))
        pictureInPicture = controller
        return controller
    }

    func close() {
        lock.lock()
        closed = true
        generation += 1
        let tracks = [video, audio]
        lock.unlock()
        // A demuxer waiting on the network returns at once, so the queues below drain.
        tracks.forEach { $0?.cancel() }
        control.sync {
            watchdog?.cancel()
            synchronizer.rate = 0
            displayLayer.sampleBufferRenderer.stopRequestingMediaData()
            audioRenderer.stopRequestingMediaData()
            videoQueue.sync {}
            audioQueue.sync {}
            // The last picture stays on screen; queued media goes.
            displayLayer.sampleBufferRenderer.flush()
            audioRenderer.flush()
            lock.lock()
            video = nil
            audio = nil
            lock.unlock()
        }
        DispatchQueue.main.async { self.pictureInPicture = nil }
    }

    // MARK: Opening

    /// Opens a video file and an audio file (either may be absent) and starts feeding the renderers.
    func open(video videoURL: URL?, audio audioURL: URL?, durationMs: Int64) {
        lock.lock()
        let generation = self.generation
        lock.unlock()
        control.async { [self] in
            do {
                let videoTrack = try videoURL.map { try TrackDecoder(url: $0, kind: .video) }
                let audioTrack = try audioURL.map { try TrackDecoder(url: $0, kind: .audio) }
                lock.lock()
                guard generation == self.generation, !closed else {
                    lock.unlock()
                    return
                }
                video = videoTrack
                audio = audioTrack
                let known = max(videoTrack?.durationSeconds ?? 0, audioTrack?.durationSeconds ?? 0)
                duration = durationMs > 0 ? durationMs : Int64(known * 1000)
                opened = true
                let start = startAt
                seekTarget = start
                clockParked = true
                lock.unlock()
                if CMTimeCompare(start, .zero) > 0 {
                    videoTrack?.seek(to: start)
                    audioTrack?.seek(to: start)
                }
                if videoTrack != nil { synchronizer.addRenderer(displayLayer.sampleBufferRenderer) }
                if audioTrack != nil { synchronizer.addRenderer(audioRenderer) }
                observeAutomaticFlushes()
                synchronizer.setRate(0, time: start)
                startFeeding(generation)
                startWatchdog()
            } catch {
                fail("\(error)")
            }
        }
    }

    private func startFeeding(_ generation: Int) {
        lock.lock()
        let hasVideo = video != nil, hasAudio = audio != nil
        lock.unlock()
        if hasVideo {
            displayLayer.sampleBufferRenderer.requestMediaDataWhenReady(on: videoQueue) { [weak self] in self?.feedVideo(generation) }
        }
        if hasAudio {
            audioRenderer.requestMediaDataWhenReady(on: audioQueue) { [weak self] in self?.feedAudio(generation) }
        }
    }

    // MARK: Feeding (on videoQueue and audioQueue)

    private func track(_ kind: TrackDecoder.Kind, _ generation: Int) -> TrackDecoder? {
        lock.lock()
        defer { lock.unlock() }
        guard generation == self.generation, !closed else { return nil }
        return kind == .video ? video : audio
    }

    private func feedVideo(_ generation: Int) {
        let renderer = displayLayer.sampleBufferRenderer
        while renderer.isReadyForMoreMediaData {
            guard let decoder = track(.video, generation) else { return }
            let next: CMSampleBuffer?
            do {
                next = try decoder.next()
            } catch {
                renderer.stopRequestingMediaData()
                fail("\(error)", generation)
                return
            }
            let skipped = decoder.takeSkippedFrames()
            guard let sample = next else {
                renderer.stopRequestingMediaData()
                update(generation) {
                    framesDropped += skipped
                    videoEnded = true
                }
                return
            }
            let time = CMSampleBufferGetPresentationTimeStamp(sample)
            let end = CMTimeAdd(time, CMSampleBufferGetDuration(sample).isValid ? CMSampleBufferGetDuration(sample) : .zero)
            let clock = synchronizer.currentTime()
            let late = synchronizer.rate > 0 ? CMTimeSubtract(clock, time) : .zero
            var show = false
            var startsClock = false
            update(generation) {
                framesDecoded += 1
                framesDropped += skipped
                pictureSize = decoder.pictureSize
                pixelFormat = decoder.pixelFormatName
                colorPrimaries = decoder.colourPrimariesName
                transferFunction = decoder.transferFunctionName
                // A seek decodes from the keyframe before its target; the frames ending before the target are not shown.
                if CMTimeCompare(end, seekTarget) <= 0 && CMTimeCompare(time, seekTarget) < 0 { return }
                if CMTimeCompare(late, Self.lateTolerance) > 0 {
                    framesDropped += 1
                    return
                }
                show = true
                framesRendered += 1
                maxLateMs = max(maxLateMs, CMTimeGetSeconds(late) * 1000)
                if seekLandedMs < 0 { seekLandedMs = Int64(CMTimeGetSeconds(time) * 1000) }
                startsClock = clockParked
            }
            if CMTimeCompare(late, Self.catchUpAfter) > 0 { decoder.skipToNextKeyframe() }
            guard show else { continue }
            if let videoTap, let pixels = CMSampleBufferGetImageBuffer(sample) { videoTap(pixels, time) }
            renderer.enqueue(sample)
            if startsClock { control.async { self.startClock(generation) } }
        }
    }

    private func feedAudio(_ generation: Int) {
        while audioRenderer.isReadyForMoreMediaData {
            guard let decoder = track(.audio, generation) else { return }
            let next: CMSampleBuffer?
            do {
                next = try decoder.next()
            } catch {
                audioRenderer.stopRequestingMediaData()
                fail("\(error)", generation)
                return
            }
            guard let sample = next else {
                audioRenderer.stopRequestingMediaData()
                update(generation) { audioEnded = true }
                return
            }
            let time = CMSampleBufferGetPresentationTimeStamp(sample)
            let end = CMTimeAdd(time, CMSampleBufferGetDuration(sample))
            var queue = false
            var startsClock = false
            update(generation) {
                if CMTimeCompare(end, seekTarget) <= 0 { return }
                queue = true
                audioQueuedMs += CMTimeGetSeconds(CMSampleBufferGetDuration(sample)) * 1000
                audioQueuedUntil = end
                if video == nil {
                    if seekLandedMs < 0 { seekLandedMs = Int64(CMTimeGetSeconds(time) * 1000) }
                    startsClock = clockParked
                }
            }
            guard queue else { continue }
            if let audioTap { tap(sample, time, audioTap) }
            audioRenderer.enqueue(sample)
            if startsClock { control.async { self.startClock(generation) } }
        }
    }

    // The first frame (or, with no video, the first sound) after opening or a seek is queued: the clock may run. It
    // still waits for the sound to be ready (delaysRateChangeUntilHasSufficientMediaData).
    private func startClock(_ generation: Int) {
        lock.lock()
        guard generation == self.generation, !closed, clockParked else {
            lock.unlock()
            return
        }
        clockParked = false
        let rate: Float = wantsToPlay ? 1 : 0
        let at = seekTarget
        lock.unlock()
        synchronizer.setRate(rate, time: at)
        invalidatePictureInPicture()
    }

    private func tap(_ sample: CMSampleBuffer, _ time: CMTime, _ audioTap: (UnsafeBufferPointer<Float>, Int, Double, CMTime) -> Void) {
        guard let block = CMSampleBufferGetDataBuffer(sample),
              let description = CMSampleBufferGetFormatDescription(sample),
              let format = CMAudioFormatDescriptionGetStreamBasicDescription(description)?.pointee else { return }
        var length = 0
        var pointer: UnsafeMutablePointer<CChar>?
        guard CMBlockBufferGetDataPointer(block, atOffset: 0, lengthAtOffsetOut: nil, totalLengthOut: &length, dataPointerOut: &pointer) == kCMBlockBufferNoErr,
              let data = pointer else { return }
        data.withMemoryRebound(to: Float.self, capacity: length / 4) { floats in
            audioTap(UnsafeBufferPointer(start: floats, count: length / 4), Int(format.mChannelsPerFrame), format.mSampleRate, time)
        }
    }

    // MARK: Seeking (on control)

    private func seek(to target: CMTime) {
        lock.lock()
        guard opened, !closed else {
            lock.unlock()
            return
        }
        generation += 1
        let next = generation
        let tracks = (video, audio)
        lock.unlock()
        synchronizer.setRate(0, time: synchronizer.currentTime())
        displayLayer.sampleBufferRenderer.stopRequestingMediaData()
        audioRenderer.stopRequestingMediaData()
        // Wait out a feed already running; it sees the new generation and returns.
        videoQueue.sync {}
        audioQueue.sync {}
        displayLayer.sampleBufferRenderer.flush()
        audioRenderer.flush()
        tracks.0?.seek(to: target)
        tracks.1?.seek(to: target)
        lock.lock()
        seekTarget = target
        clockParked = true
        videoEnded = false
        audioEnded = false
        audioQueuedUntil = .invalid
        audioStarving = false
        lock.unlock()
        synchronizer.setRate(0, time: target)
        startFeeding(next)
    }

    // MARK: Watching

    // Four times a second: a gap in the sound, the end of both tracks, a renderer that failed.
    private func startWatchdog() {
        let timer = DispatchSource.makeTimerSource(queue: control)
        timer.schedule(deadline: .now() + .milliseconds(250), repeating: .milliseconds(250))
        timer.setEventHandler { [weak self] in self?.watch() }
        timer.resume()
        watchdog = timer
    }

    private func watch() {
        let clock = synchronizer.currentTime()
        let playing = synchronizer.rate > 0
        // After the app was in the background the video renderer must be flushed: refill both from the clock.
        if displayLayer.sampleBufferRenderer.requiresFlushToResumeDecoding {
            seek(to: clock)
            return
        }
        if audioRenderer.status == .failed { fail("audio renderer: \(audioRenderer.error?.localizedDescription ?? "failed")") }
        if displayLayer.sampleBufferRenderer.status == .failed {
            fail("video renderer: \(displayLayer.sampleBufferRenderer.error?.localizedDescription ?? "failed")")
        }
        lock.lock()
        let hasAudio = audio != nil, hasVideo = video != nil
        let ended = (!hasVideo || videoEnded) && (!hasAudio || audioEnded)
        if playing && hasAudio && !audioEnded && audioQueuedUntil.isValid {
            let starving = CMTimeCompare(clock, audioQueuedUntil) >= 0
            if starving && !audioStarving { underruns += 1 }
            audioStarving = starving
        }
        // At the end the clock stops; the wish to play stays, so a seek back plays on, as in ExoPlayer.
        let finished = ended && playing && (!hasAudio || !audioQueuedUntil.isValid || CMTimeCompare(clock, audioQueuedUntil) >= 0)
        lock.unlock()
        if finished { synchronizer.rate = 0 }
    }

    // An audio route change (headphones, AirPlay) flushes the audio renderer; refill it from where the clock is.
    private func observeAutomaticFlushes() {
        guard flushObserver == nil else { return }
        flushObserver = NotificationCenter.default.addObserver(
            forName: .AVSampleBufferAudioRendererWasFlushedAutomatically, object: audioRenderer, queue: nil
        ) { [weak self] _ in
            guard let self else { return }
            let position = currentPositionMs()
            control.async { self.seek(to: CMTime(value: position, timescale: 1000)) }
        }
    }

    // MARK: Helpers

    private func update(_ generation: Int, _ change: () -> Void) {
        lock.lock()
        defer { lock.unlock() }
        guard generation == self.generation else { return }
        change()
    }

    private func fail(_ message: String, _ generation: Int? = nil) {
        lock.lock()
        let current = generation == nil || generation == self.generation
        if current && !closed && failure == nil { failure = message }
        lock.unlock()
        if current { control.async { self.synchronizer.rate = 0 } }
    }

    private func invalidatePictureInPicture() {
        DispatchQueue.main.async { self.pictureInPicture?.invalidatePlaybackState() }
    }
}

// Picture in Picture drives the engine through these while its window is up.
extension BundledEngine: AVPictureInPictureSampleBufferPlaybackDelegate {
    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, setPlaying playing: Bool) {
        playing ? play() : pause()
    }

    func pictureInPictureControllerTimeRangeForPlayback(_ pictureInPictureController: AVPictureInPictureController) -> CMTimeRange {
        let length = durationMs()
        return length > 0
            ? CMTimeRange(start: .zero, duration: CMTime(value: length, timescale: 1000))
            : CMTimeRange(start: .negativeInfinity, duration: .positiveInfinity)
    }

    func pictureInPictureControllerIsPlaybackPaused(_ pictureInPictureController: AVPictureInPictureController) -> Bool {
        !isPlaying()
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {}

    func pictureInPictureController(
        _ pictureInPictureController: AVPictureInPictureController,
        skipByInterval skipInterval: CMTime,
        completion completionHandler: @escaping () -> Void
    ) {
        seekTo(positionMs: max(0, currentPositionMs() + Int64(CMTimeGetSeconds(skipInterval) * 1000)))
        completionHandler()
    }
}
