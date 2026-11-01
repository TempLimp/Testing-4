import AVFoundation
import AVKit
@testable import Flow
import Shared
import XCTest

/// The bundled engine (S04b) on short clips shaped like YouTube's files (tools/make_engine_clips.py): video and audio in
/// separate files, read through HTTP byte ranges as the engine reads LocalHlsProxy's. Each clip turns white, and sounds
/// a 1 kHz tone, at exactly 2.000 s, so A/V drift is the gap between the two onsets as the engine decoded them.
///
/// Bounds: decoded onsets within 5 ms of each other and of 2.000 s (an Opus pre-skip or AAC priming mistake is 6.5 or
/// 21 ms); no shown frame more than the engine's 40 ms late tolerance behind the clock; a seek's first frame within one
/// frame (33.4 ms at 30 fps) of its target; the clock within 15 % of real time. A shared runner can stall the decoding
/// queues for a moment (run 41300385579: 28 frames dropped in one test); dropping late frames is the engine keeping
/// sync, so up to a quarter may go, while the 40 ms bound on shown frames stays.
final class BundledEngineTests: XCTestCase {
    private static let onset = 2.0

    override class func setUp() {
        super.setUp()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ClipProtocol.self]
        RangeReader.session = URLSession(configuration: configuration)
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    func testVp9WithOpusPlaysInSync() throws {
        try playsInSync(video: "vp9.webm", audio: "opus.webm", decoders: ("vp9", "opus"))
    }

    // CI's simulator dropped no 10-bit frame in the runs of 2026-10-31 and 28-33 of 139 (mostly by skipping to a
    // keyframe after a stall of about a second) in all three of 2026-11-01, same image and Xcode, while every 8-bit test
    // dropped none and shown frames stayed within 40 ms. testDecodersRunFasterThanRealTime tells whether decoding or the
    // simulator's display of P010 is the slow part; this test bounds sync and decoding, and half the frames may drop.
    func testTenBitVp9WithOpusPlaysInSync() throws {
        try playsInSync(video: "vp9-10bit.webm", audio: "opus.webm", decoders: ("vp9", "opus"), allowedDropShare: 0.5)
    }

    // Each decoder alone, no renderer: 6 s of 30 fps decoded and converted to the display's pixel format.
    func testDecodersRunFasterThanRealTime() throws {
        var speeds: [String: Any] = [:]
        for name in ["vp9.webm", "vp9-10bit.webm", "av1.mp4"] {
            let decoder = try TrackDecoder(url: clip(name), kind: .video)
            let started = Date()
            var frames = 0
            while try decoder.next() != nil { frames += 1 }
            let seconds = Date().timeIntervalSince(started)
            XCTAssertEqual(frames, 180, name)
            XCTAssertLessThan(seconds, 1.5, "\(name): 6 s of video took \(seconds) s to decode")
            speeds[name] = seconds * 1000 / Double(max(frames, 1))
        }
        record("decodeMsPerFrame", speeds)
    }

    func testAv1WithOpusPlaysInSync() throws {
        try playsInSync(video: "av1.mp4", audio: "opus.webm", decoders: ("libdav1d", "opus"))
    }

    func testVp9WithAacPlaysInSync() throws {
        try playsInSync(video: "vp9.webm", audio: "aac.m4a", decoders: ("vp9", "aac"))
    }

    func testOpusAlonePlays() throws {
        let engine = BundledEngine()
        let tone = Onset()
        engine.audioTap = tone.listen
        engine.open(video: nil, audio: clip("opus.webm"), durationMs: 0)
        engine.play()
        let (clock, _) = clockRate(engine, from: 1_000, to: 3_000)
        let stats = engine.stats()
        XCTAssertNil(engine.error())
        XCTAssertEqual(stats.audioDecoder, "opus")
        XCTAssertNil(stats.videoDecoder)
        XCTAssertTrue(stats.audioRendering)
        XCTAssertGreaterThanOrEqual(stats.audioQueuedMs, 3_000)
        XCTAssertEqual(engine.durationMs(), 6_008, accuracy: 20)
        XCTAssertEqual(clock, 1, accuracy: 0.15, "clock ran at \(clock)x real time")
        let toneAt = try XCTUnwrap(tone.seconds, "no tone decoded")
        XCTAssertEqual(toneAt, Self.onset, accuracy: 0.005)
        record("opus", ["toneAt": toneAt, "audioQueuedMs": stats.audioQueuedMs, "underruns": stats.audioUnderruns])
        engine.close()
    }

    func testASeekLandsOnItsTargetAndPlaysOn() throws {
        let engine = try start(video: "vp9.webm", audio: "opus.webm")
        waitUntil(engine, reaches: 1_000)
        var landings: [String: Any] = [:]
        // On a keyframe (every second) and between two.
        for target in [Int64(3_000), 4_500] {
            engine.seekTo(positionMs: target)
            waitUntil(engine, reaches: target + 300) { $0.stats().seekLandedMs >= 0 }
            let landed = engine.stats().seekLandedMs
            XCTAssertNil(engine.error())
            XCTAssertGreaterThanOrEqual(landed, target - 34, "seek to \(target) landed at \(landed)")
            XCTAssertLessThanOrEqual(landed, target, "seek to \(target) landed at \(landed)")
            XCTAssertGreaterThan(engine.currentPositionMs(), target)
            landings["\(target)"] = landed
        }
        // Back to before the flash: the onsets are found again, as decoded after the seek.
        engine.seekTo(positionMs: 1_000)
        waitUntil(engine, reaches: 2_500) { $0.stats().seekLandedMs >= 0 }
        XCTAssertNil(engine.error())
        record("seek", landings)
        engine.close()
    }

    func testPictureInPictureCanTakeTheSampleBufferLayer() throws {
        // CI's iPhone simulator reports no Picture in Picture (run 41241274318); the phone's playback gate checks it there.
        guard AVPictureInPictureController.isPictureInPictureSupported() else {
            record("pictureInPicture", ["supported": false])
            throw XCTSkip("Picture in Picture is not supported on this simulator")
        }
        let engine = try start(video: "vp9.webm", audio: "opus.webm")
        waitUntil(engine, reaches: 500)
        let controller = try XCTUnwrap(engine.pictureInPictureController())
        XCTAssertTrue(controller.contentSource?.sampleBufferDisplayLayer === engine.layer)
        let deadline = Date().addingTimeInterval(10)
        while !controller.isPictureInPicturePossible && Date() < deadline { RunLoop.current.run(until: Date().addingTimeInterval(0.1)) }
        record("pictureInPicture", ["possible": controller.isPictureInPicturePossible])
        XCTAssertTrue(controller.isPictureInPicturePossible, "Picture in Picture never became possible")
        engine.close()
    }

    // MARK: Helpers

    private func playsInSync(video: String, audio: String, decoders: (String, String), allowedDropShare: Double = 0.25) throws {
        let flash = Onset(), tone = Onset()
        let engine = BundledEngine()
        engine.videoTap = flash.watch
        engine.audioTap = tone.listen
        show(engine)
        engine.open(video: clip(video), audio: clip(audio), durationMs: 6_000)
        engine.play()
        let (clock, droppedByFirstSecond) = clockRate(engine, from: 1_000, to: 4_500)
        let stats = engine.stats()
        XCTAssertNil(engine.error())
        XCTAssertEqual(stats.videoDecoder, decoders.0)
        XCTAssertEqual(stats.audioDecoder, decoders.1)
        XCTAssertEqual(stats.videoSize, "320x180")
        // 4.5 s at 30 fps is 135 frames; dropped ones include those skipped, never decoded, after a stall.
        XCTAssertGreaterThanOrEqual(Double(stats.framesRendered), 135 * (1 - allowedDropShare), "frames rendered")
        XCTAssertLessThanOrEqual(Double(stats.framesDropped), 135 * allowedDropShare, "frames dropped")
        XCTAssertTrue(stats.audioRendering, "audio renderer not rendering")
        XCTAssertGreaterThanOrEqual(stats.audioQueuedMs, 4_500)
        XCTAssertLessThanOrEqual(stats.maxVideoLateMs, 40)
        XCTAssertEqual(clock, 1, accuracy: 0.15, "clock ran at \(clock)x real time")
        let flashAt = try XCTUnwrap(flash.seconds, "no white frame shown")
        let toneAt = try XCTUnwrap(tone.seconds, "no tone decoded")
        XCTAssertEqual(flashAt, Self.onset, accuracy: 0.001)
        XCTAssertEqual(toneAt, Self.onset, accuracy: 0.005)
        XCTAssertEqual(toneAt - flashAt, 0, accuracy: 0.005, "A/V drift")
        record(video + "+" + audio, [
            "flashAt": flashAt, "toneAt": toneAt, "driftMs": (toneAt - flashAt) * 1000,
            "framesRendered": stats.framesRendered, "framesDropped": stats.framesDropped,
            "maxVideoLateMs": stats.maxVideoLateMs, "audioUnderruns": stats.audioUnderruns, "clockRate": clock,
            // Whether drops come at the start (a one-time stall) or spread over the clip (steady slowness).
            "droppedByFirstSecond": droppedByFirstSecond,
        ])
        engine.close()
    }

    private func start(video: String, audio: String) throws -> BundledEngine {
        let engine = BundledEngine()
        show(engine)
        engine.open(video: clip(video), audio: clip(audio), durationMs: 6_000)
        engine.play()
        return engine
    }

    private func clip(_ name: String) -> URL {
        URL(string: "clip:///\(name)")!
    }

    // The renderers draw into the host app's window, as they would in the player.
    private func show(_ engine: BundledEngine) {
        let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
        engine.layer.frame = window?.bounds ?? CGRect(x: 0, y: 0, width: 320, height: 180)
        window?.layer.addSublayer(engine.layer)
    }

    private func waitUntil(_ engine: BundledEngine, reaches positionMs: Int64, and condition: (BundledEngine) -> Bool = { _ in true }) {
        let deadline = Date().addingTimeInterval(20)
        while (engine.currentPositionMs() < positionMs || !condition(engine)) && engine.error() == nil && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
    }

    // The clock follows the sound device and must keep real time: media seconds over wall seconds between two
    // positions read well before the clip ends (a runner slow to wake this thread then cannot run it into the end).
    private func clockRate(_ engine: BundledEngine, from: Int64, to: Int64) -> (rate: Double, droppedAtStart: Int32) {
        waitUntil(engine, reaches: from)
        let (startPosition, startTime, droppedAtStart) = (engine.currentPositionMs(), Date(), engine.stats().framesDropped)
        waitUntil(engine, reaches: to)
        let (endPosition, endTime) = (engine.currentPositionMs(), Date())
        XCTAssertGreaterThanOrEqual(endPosition, to, "the clock did not reach \(to) ms")
        return (Double(endPosition - startPosition) / 1000 / endTime.timeIntervalSince(startTime), droppedAtStart)
    }

    // One line per check in Documents/engine-tests.jsonl, which CI copies into its evidence.
    private func record(_ name: String, _ values: [String: Any]) {
        guard let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first,
              let json = try? JSONSerialization.data(withJSONObject: ["test": name].merging(values.mapValues { "\($0)" }) { a, _ in a }),
              let line = String(data: json, encoding: .utf8) else { return }
        print("ENGINE TEST: \(line)")
        let file = documents.appendingPathComponent("engine-tests.jsonl")
        if let handle = try? FileHandle(forWritingTo: file) {
            handle.seekToEndOfFile()
            handle.write(Data((line + "\n").utf8))
            handle.closeFile()
        } else {
            try? Data((line + "\n").utf8).write(to: file)
        }
    }
}

/// The time the first white frame, or the first loud sample, was decoded at.
private final class Onset {
    private let lock = NSLock()
    private var found: Double?

    var seconds: Double? {
        lock.lock()
        defer { lock.unlock() }
        return found
    }

    func watch(_ pixels: CVPixelBuffer, _ time: CMTime) {
        guard seconds == nil else { return }
        CVPixelBufferLockBaseAddress(pixels, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixels, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddressOfPlane(pixels, 0) else { return }
        let row = CVPixelBufferGetHeightOfPlane(pixels, 0) / 2, column = CVPixelBufferGetWidthOfPlane(pixels, 0) / 2
        let stride = CVPixelBufferGetBytesPerRowOfPlane(pixels, 0)
        // NV12 holds 8-bit luma; P010 holds it in the top 10 of 16 bits.
        let deep = CVPixelBufferGetPixelFormatType(pixels) == kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange
        let luma = deep
            ? Int(base.advanced(by: row * stride + column * 2).load(as: UInt16.self) >> 8)
            : Int(base.advanced(by: row * stride + column).load(as: UInt8.self))
        if luma > 128 { mark(CMTimeGetSeconds(time)) }
    }

    func listen(_ samples: UnsafeBufferPointer<Float>, _ channels: Int, _ rate: Double, _ time: CMTime) {
        guard seconds == nil else { return }
        // Above the codec's pre-echo (under 0.05) and below the tone's 0.125 peak.
        var index = 0
        while index < samples.count {
            if abs(samples[index]) > 0.08 {
                mark(CMTimeGetSeconds(time) + Double(index / channels) / rate)
                return
            }
            index += channels
        }
    }

    private func mark(_ value: Double) {
        lock.lock()
        if found == nil { found = value }
        lock.unlock()
    }
}

/// Serves the test bundle's clips at clip:///name, honouring Range as LocalHlsProxy's file route does.
final class ClipProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.scheme == "clip"
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let url = request.url,
              let file = Bundle(for: ClipProtocol.self).url(forResource: url.lastPathComponent, withExtension: nil),
              let data = try? Data(contentsOf: file) else {
            client?.urlProtocol(self, didFailWithError: URLError(.fileDoesNotExist))
            return
        }
        var status = 200
        var body = data
        var headers = ["Accept-Ranges": "bytes"]
        if let range = request.value(forHTTPHeaderField: "Range"), range.hasPrefix("bytes=") {
            let bounds = range.dropFirst(6).split(separator: "-", omittingEmptySubsequences: false)
            let start = Int(bounds.first ?? "") ?? 0
            let end = min(Int(bounds.count > 1 ? bounds[1] : "") ?? data.count - 1, data.count - 1)
            guard start < data.count, start <= end else {
                let refused = HTTPURLResponse(url: url, statusCode: 416, httpVersion: "HTTP/1.1", headerFields: ["Content-Range": "bytes */\(data.count)"])!
                client?.urlProtocol(self, didReceive: refused, cacheStoragePolicy: .notAllowed)
                client?.urlProtocolDidFinishLoading(self)
                return
            }
            body = data.subdata(in: start..<end + 1)
            status = 206
            headers["Content-Range"] = "bytes \(start)-\(end)/\(data.count)"
        }
        headers["Content-Length"] = "\(body.count)"
        let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
