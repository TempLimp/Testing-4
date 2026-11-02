import FFmpeg
import Foundation

/// What TrackDecoder reads a track from: a RangeReader for a file, a StreamReader for a stream still arriving.
protocol ByteSource: AnyObject {
    /// FFmpeg's read_packet: up to [count] bytes at the current position, or an AVERROR.
    func read(into buffer: UnsafeMutablePointer<UInt8>, count: Int) -> Int32
    /// Ends every wait, so a demuxer blocked on the network returns AVERROR_EXIT.
    func cancel()
    var error: String? { get }
}

extension RangeReader: ByteSource {}

/// A track LocalHlsProxy sends as it arrives (a SABR session's): one request, read in order as its bytes come, as
/// upstream's ExoPlayer read its progressive SABR pipes. It cannot seek, so FFmpeg reads it as a live stream; the player
/// rebuilds the session to seek elsewhere, as upstream does (sabrSeekTo).
///
/// What arrives waits here until FFmpeg reads it. Past [highWater] unread bytes the response is held back, which holds
/// back the proxy and so the session's playhead: the server then sends no further ahead than the engine reads.
final class StreamReader: NSObject, ByteSource, URLSessionDataDelegate {
    private static let highWater = 4 << 20

    let url: URL
    private let lock = NSCondition()
    private var pending = Data()
    private var consumed = 0
    private var finished = false
    private var failure: String?
    private var cancelled = false
    private var held = false
    private var session: URLSession?
    private var task: URLSessionDataTask?

    init(url: URL) {
        self.url = url
        super.init()
        let session = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)
        self.session = session
        let task = session.dataTask(with: url)
        self.task = task
        task.resume()
    }

    func read(into buffer: UnsafeMutablePointer<UInt8>, count: Int) -> Int32 {
        lock.lock()
        defer { lock.unlock() }
        while pending.count == consumed && !finished && failure == nil && !cancelled { lock.wait() }
        if cancelled { return flow_averror_exit() }
        let available = pending.count - consumed
        guard available > 0 else { return failure == nil ? flow_averror_eof() : flow_averror_eio() }
        let length = min(count, available)
        let from = consumed
        pending.withUnsafeBytes { bytes in
            buffer.update(from: bytes.baseAddress!.advanced(by: from).assumingMemoryBound(to: UInt8.self), count: length)
        }
        consumed += length
        // Read bytes go once a megabyte has piled up, so each copy of what is left stays rare.
        if consumed >= 1 << 20 {
            // A copy starting at index 0: Data's own removals can leave a non-zero startIndex.
            pending = pending.subdata(in: (pending.startIndex + consumed)..<pending.endIndex)
            consumed = 0
        }
        if held && pending.count - consumed < Self.highWater / 2 {
            held = false
            task?.resume()
        }
        return Int32(length)
    }

    func cancel() {
        lock.lock()
        cancelled = true
        lock.broadcast()
        lock.unlock()
        session?.invalidateAndCancel()
    }

    var error: String? {
        lock.lock()
        defer { lock.unlock() }
        return failure
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            lock.lock()
            failure = "\(url.lastPathComponent): HTTP \(http.statusCode)"
            lock.broadcast()
            lock.unlock()
            completionHandler(.cancel)
            return
        }
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        lock.lock()
        pending.append(data)
        if !held && pending.count - consumed > Self.highWater {
            held = true
            dataTask.suspend()
        }
        lock.broadcast()
        lock.unlock()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        if let error, !cancelled, failure == nil { failure = "\(url.lastPathComponent): \(error.localizedDescription)" }
        finished = true
        lock.broadcast()
        lock.unlock()
        // The session holds its delegate until invalidated.
        session.finishTasksAndInvalidate()
    }
}

/// FFmpeg's read callback over any ByteSource passed as the opaque pointer.
let byteSourceRead: @convention(c) (UnsafeMutableRawPointer?, UnsafeMutablePointer<UInt8>?, Int32) -> Int32 = { opaque, buffer, size in
    guard let opaque, let buffer, let source = Unmanaged<AnyObject>.fromOpaque(opaque).takeUnretainedValue() as? ByteSource else {
        return flow_averror_eio()
    }
    return source.read(into: buffer, count: Int(size))
}
