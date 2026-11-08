import AVFoundation
@testable import Flow
import Network
import Shared
import XCTest

/// S05's offline proof: a SABR exchange recorded from GVS (iosAppTests/Sabr, Big Buck Bunny, CC BY 3.0) replayed from a
/// loopback server through the whole iOS chain: upstream's session (SabrMediaSourceFactory), LocalHlsProxy, and the engine
/// the formats choose. The H.264 session plays in AVPlayer as HLS; the VP9 session in the bundled engine as two streams.
/// YouTube walls CI's runners off, so this is where SABR media is seen to play on CI; the phone's gate plays it live.
final class SabrReplayTests: XCTestCase {
    override class func setUp() {
        super.setUp()
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    func testAnH264SabrSessionPlaysInAVPlayer() throws {
        let (result, server) = try open("h264")
        defer {
            result.session.close()
            server.stop()
        }
        XCTAssertEqual(result.mediaSource.engine, .avplayer)
        let engine = AvPlayerEngine()
        show(engine.layer)
        engine.prepare(source: result.mediaSource)
        let item = try XCTUnwrap(engine.item)
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: nil)
        item.add(output)
        engine.play()
        var frames = 0
        var frameSize = ""
        var firstTime: Double?
        var lastTime = 0.0
        let deadline = Date().addingTimeInterval(60)
        while Date() < deadline && item.status != .failed {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
            guard item.status == .readyToPlay else { continue }
            let time = CMTimeGetSeconds(item.currentTime())
            if firstTime == nil && time > 0 { firstTime = time }
            lastTime = time
            let itemTime = output.itemTime(forHostTime: CACurrentMediaTime())
            if output.hasNewPixelBuffer(forItemTime: itemTime), let buffer = output.copyPixelBuffer(forItemTime: itemTime, itemTimeForDisplay: nil) {
                frames += 1
                frameSize = "\(CVPixelBufferGetWidth(buffer))x\(CVPixelBufferGetHeight(buffer))"
            }
            if lastTime - (firstTime ?? lastTime) >= 6 { break }
        }
        let tracks = item.tracks.compactMap { $0.assetTrack?.mediaType }
        let advanced = lastTime - (firstTime ?? lastTime)
        record("sabrH264AVPlayer", [
            "status": item.status.rawValue, "error": item.error?.localizedDescription ?? "", "frames": frames, "frameSize": frameSize,
            "secondsAdvanced": advanced, "tracks": tracks.map(\.rawValue).joined(separator: ","), "requests": server.requestCount,
        ])
        XCTAssertNil(item.error)
        XCTAssertEqual(item.status, .readyToPlay)
        XCTAssertTrue(tracks.contains(.video) && tracks.contains(.audio), "tracks: \(tracks)")
        XCTAssertGreaterThanOrEqual(advanced, 6, "the clock advanced \(advanced) s")
        // About 30 fps over 6 s, sampled each 50 ms of a busy runner: a lower bound only.
        XCTAssertGreaterThanOrEqual(frames, 60, "frames decoded")
        XCTAssertEqual(frameSize, "256x144")
        engine.close()
    }

    func testAVp9SabrSessionPlaysInTheBundledEngine() throws {
        let (result, server) = try open("vp9")
        defer {
            result.session.close()
            server.stop()
        }
        XCTAssertEqual(result.mediaSource.engine, .bundled)
        XCTAssertTrue(result.mediaSource.streamed)
        let engine = BundledEngine()
        let sound = Loudness()
        engine.audioTap = sound.listen
        show(engine.layer)
        engine.prepare(source: result.mediaSource)
        engine.play()
        let deadline = Date().addingTimeInterval(60)
        while engine.currentPositionMs() < 7_000 && engine.error() == nil && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        let stats = engine.stats()
        record("sabrVp9Bundled", [
            "error": engine.error() ?? "", "positionMs": engine.currentPositionMs(), "videoDecoder": stats.videoDecoder ?? "",
            "audioDecoder": stats.audioDecoder ?? "", "videoSize": stats.videoSize ?? "", "framesRendered": stats.framesRendered,
            "framesDropped": stats.framesDropped, "maxVideoLateMs": stats.maxVideoLateMs, "audioQueuedMs": stats.audioQueuedMs,
            "audioUnderruns": stats.audioUnderruns, "peakSample": sound.peak, "requests": server.requestCount,
        ])
        XCTAssertNil(engine.error())
        XCTAssertGreaterThanOrEqual(engine.currentPositionMs(), 7_000)
        XCTAssertEqual(stats.videoDecoder, "vp9")
        XCTAssertEqual(stats.audioDecoder, "opus")
        XCTAssertEqual(stats.videoSize, "256x144")
        // 7 s at 30 fps is 210 frames; as in BundledEngineTests a quarter may drop on a stalled runner.
        XCTAssertGreaterThanOrEqual(Double(stats.framesRendered), 210 * 0.75, "frames rendered")
        XCTAssertLessThanOrEqual(stats.maxVideoLateMs, 40)
        XCTAssertTrue(stats.audioRendering, "audio renderer not rendering")
        XCTAssertGreaterThanOrEqual(stats.audioQueuedMs, 7_000)
        // Big Buck Bunny's opening has sound well above digital silence.
        XCTAssertGreaterThan(sound.peak, 0.01, "the decoded audio is silent")
        engine.close()
    }

    // MARK: Helpers

    /// The recorded session [name], served from a loopback server, opened as upstream's factory opens a live one.
    private func open(_ name: String) throws -> (SabrMediaSourceResult, SabrReplayServer) {
        let manifest = try JSONSerialization.jsonObject(with: Data(contentsOf: try resource("manifest", "json"))) as? [String: Any]
        let sessions = try XCTUnwrap(manifest?["sessions"] as? [[String: Any]])
        let session = try XCTUnwrap(sessions.first { $0["name"] as? String == name })
        let count = try XCTUnwrap(session["responses"] as? Int)
        let responses = try (1...count).map { try Data(contentsOf: resource("\(name)-\($0)", "ump")) }
        let server = try SabrReplayServer(responses: responses)
        let int = { (key: String) in (session[key] as? NSNumber)?.int64Value ?? 0 }
        let text = { (key: String) in session[key] as? String ?? "" }
        let config = KotlinByteArray(size: 7)
        for (index, byte) in Array("fixture".utf8).enumerated() { config.set(index: Int32(index), value: Int8(byte)) }
        let info = SabrStreamInfo(
            streamingUrl: "http://127.0.0.1:\(server.port)/videoplayback?sabr=1",
            audioItag: Int32(int("audioItag")), audioLmt: int("audioLmt"), videoItag: Int32(int("videoItag")), videoLmt: int("videoLmt"),
            audioXtags: "", videoXtags: "", durationMs: int("durationMs"), poToken: "fixture", visitorId: "fixture",
            ustreamerConfig: config, audioMimeType: text("audioMimeType"), videoMimeType: text("videoMimeType"), audioTrackId: "",
            targetHeight: Int32(int("videoHeight")), videoHeight: Int32(int("videoHeight")), cpn: "",
            clientNameId: 1, clientVersion: "", clientUserAgent: ""
        )
        // A Kotlin suspend function: called on the main thread, which this loop keeps turning until it answers.
        var created: SabrMediaSourceResult?
        var failure: Error?
        var done = false
        SabrMediaSourceFactory.shared.create(info: info, videoId: text("videoId"), durationMs: int("durationMs"), startPositionMs: 0, mediaId: name, onError: nil) { result, error in
            created = result
            failure = error
            done = true
        }
        let deadline = Date().addingTimeInterval(20)
        while !done && Date() < deadline { RunLoop.current.run(until: Date().addingTimeInterval(0.05)) }
        if let failure { throw failure }
        return (try XCTUnwrap(created, "no engine for the \(name) session"), server)
    }

    private func resource(_ name: String, _ ext: String) throws -> URL {
        try XCTUnwrap(Bundle(for: SabrReplayTests.self).url(forResource: name, withExtension: ext), "\(name).\(ext) is not in the test bundle")
    }

    private func show(_ layer: CALayer) {
        let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
        layer.frame = window?.bounds ?? CGRect(x: 0, y: 0, width: 256, height: 144)
        window?.layer.addSublayer(layer)
    }

    // One line per check in Documents/engine-tests.jsonl, as BundledEngineTests writes.
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

/// The loudest sample the engine decoded.
private final class Loudness {
    private let lock = NSLock()
    private var loudest: Float = 0

    var peak: Float {
        lock.lock()
        defer { lock.unlock() }
        return loudest
    }

    func listen(_ samples: UnsafeBufferPointer<Float>, _ channels: Int, _ rate: Double, _ time: CMTime) {
        let local = samples.reduce(Float(0)) { max($0, abs($1)) }
        lock.lock()
        loudest = max(loudest, local)
        lock.unlock()
    }
}

/// GVS for one recorded session, on loopback: the n-th POST is answered with the n-th recorded response, every later one
/// with a request policy and no media, as GVS answers a client that already holds what it may have.
final class SabrReplayServer {
    // NEXT_REQUEST_POLICY { backoff_time_ms: 1000 } as one UMP part.
    private static let policyOnly = Data([0x23, 0x03, 0x20, 0xE8, 0x07])

    private let listener: NWListener
    private let responses: [Data]
    private let queue = DispatchQueue(label: "sabr-replay")
    private var requests = 0

    init(responses: [Data]) throws {
        self.responses = responses
        let parameters = NWParameters.tcp
        parameters.requiredInterfaceType = .loopback
        listener = try NWListener(using: parameters, on: .any)
        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { state in
            if case .ready = state { ready.signal() }
        }
        listener.newConnectionHandler = { [weak self] connection in self?.serve(connection) }
        listener.start(queue: queue)
        _ = ready.wait(timeout: .now() + 10)
    }

    var port: UInt16 { listener.port?.rawValue ?? 0 }

    var requestCount: Int { queue.sync { requests } }

    func stop() {
        listener.cancel()
    }

    private func serve(_ connection: NWConnection) {
        connection.start(queue: queue)
        receive(connection, Data())
    }

    // One request per connection: the answer says Connection: close.
    private func receive(_ connection: NWConnection, _ buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1 << 16) { [weak self] data, _, complete, error in
            guard let self else { return }
            var buffer = buffer
            if let data { buffer.append(data) }
            if let end = buffer.range(of: Data("\r\n\r\n".utf8)) {
                let head = String(decoding: buffer[buffer.startIndex..<end.lowerBound], as: UTF8.self)
                let length = head.split(separator: "\r\n").first { $0.lowercased().hasPrefix("content-length:") }
                    .flatMap { Int($0.split(separator: ":")[1].trimmingCharacters(in: .whitespaces)) } ?? 0
                if buffer.distance(from: end.upperBound, to: buffer.endIndex) >= length {
                    self.requests += 1
                    let body = self.requests <= self.responses.count ? self.responses[self.requests - 1] : Self.policyOnly
                    var answer = Data("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.yt-ump\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n".utf8)
                    answer.append(body)
                    connection.send(content: answer, completion: .contentProcessed { _ in connection.cancel() })
                    return
                }
            }
            if complete || error != nil {
                connection.cancel()
            } else {
                self.receive(connection, buffer)
            }
        }
    }
}
