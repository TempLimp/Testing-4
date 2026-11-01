import FFmpeg
import Foundation

/// A file read through HTTP byte ranges, for FFmpeg's custom I/O: the bundled engine's only way to its media, which it
/// fetches from LocalHlsProxy's file route (googlevideo's request policy is applied there, once, for both engines).
/// It fetches whole chunks and keeps a couple ahead of the demuxer, so decoding rarely waits on the network.
final class RangeReader {
    /// Tests serve local clips through their own URL protocol; the app reads from the loopback proxy.
    static var session = URLSession(configuration: .ephemeral)
    static let chunkSize: Int64 = 1 << 21
    private static let chunksAhead: Int64 = 2
    private static let attempts = 3

    let url: URL
    private let lock = NSCondition()
    private var chunks: [Int64: Data] = [:]
    private var loading: [Int64: URLSessionDataTask] = [:]
    private var failure: String?
    private var size: Int64 = -1
    private var position: Int64 = 0
    private var cancelled = false

    init(url: URL) {
        self.url = url
    }

    /// FFmpeg's read_packet: up to [count] bytes at the current position, or an AVERROR.
    func read(into buffer: UnsafeMutablePointer<UInt8>, count: Int) -> Int32 {
        lock.lock()
        defer { lock.unlock() }
        if size >= 0 && position >= size { return flow_averror_eof() }
        let index = position / Self.chunkSize
        // Ahead only once the size is known: a chunk past the end of a short file is answered 416.
        for ahead in 0...(size < 0 ? 0 : Self.chunksAhead) { request(index + ahead, attempt: 1) }
        while chunks[index] == nil && failure == nil && !cancelled { lock.wait() }
        if cancelled { return flow_averror_exit() }
        guard let chunk = chunks[index] else { return flow_averror_eio() }
        let offset = Int(position - index * Self.chunkSize)
        let available = chunk.count - offset
        if available <= 0 { return flow_averror_eof() }
        let length = min(count, available)
        chunk.withUnsafeBytes { bytes in
            buffer.update(from: bytes.baseAddress!.advanced(by: offset).assumingMemoryBound(to: UInt8.self), count: length)
        }
        position += Int64(length)
        // Behind the demuxer only the chunk before this one is kept, for the small seeks back a demuxer makes.
        for key in chunks.keys where key < index - 1 || key > index + Self.chunksAhead + 1 { chunks[key] = nil }
        return Int32(length)
    }

    /// FFmpeg's seek: a new position, or the file's size for AVSEEK_SIZE.
    func seek(offset: Int64, whence: Int32) -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        if whence & AVSEEK_SIZE != 0 { return knownSize() }
        switch whence & ~AVSEEK_FORCE {
        case SEEK_SET: position = offset
        case SEEK_CUR: position += offset
        case SEEK_END:
            let total = knownSize()
            guard total >= 0 else { return -1 }
            position = total + offset
        default: return -1
        }
        return position
    }

    /// Ends every wait, so a demuxer blocked on the network returns AVERROR_EXIT.
    func cancel() {
        lock.lock()
        cancelled = true
        loading.values.forEach { $0.cancel() }
        lock.broadcast()
        lock.unlock()
    }

    var error: String? {
        lock.lock()
        defer { lock.unlock() }
        return failure
    }

    // The first answer carries the size in its Content-Range. Called with the lock held.
    private func knownSize() -> Int64 {
        if size < 0 {
            request(0, attempt: 1)
            while size < 0 && failure == nil && !cancelled { lock.wait() }
        }
        return size
    }

    // Called with the lock held.
    private func request(_ index: Int64, attempt: Int) {
        let start = index * Self.chunkSize
        guard !cancelled, chunks[index] == nil, loading[index] == nil, size < 0 || start < size else { return }
        var request = URLRequest(url: url)
        request.setValue("bytes=\(start)-\(start + Self.chunkSize - 1)", forHTTPHeaderField: "Range")
        let task = Self.session.dataTask(with: request) { [weak self] data, response, error in
            self?.loaded(index, start: start, attempt: attempt, data: data, response: response as? HTTPURLResponse, error: error)
        }
        loading[index] = task
        task.resume()
    }

    private func loaded(_ index: Int64, start: Int64, attempt: Int, data: Data?, response: HTTPURLResponse?, error: Error?) {
        lock.lock()
        defer {
            lock.broadcast()
            lock.unlock()
        }
        loading[index] = nil
        guard !cancelled else { return }
        if response?.statusCode == 416 {
            // Range Not Satisfiable: nothing there, the end of the file (an empty one, when it is the first chunk).
            chunks[index] = Data()
            if index == 0 && size < 0 { size = 0 }
            return
        }
        guard let data, let response, error == nil, (200...299).contains(response.statusCode) else {
            if attempt < Self.attempts {
                request(index, attempt: attempt + 1)
            } else {
                failure = "\(url.lastPathComponent) bytes \(start)-: " + (error.map { "\($0.localizedDescription)" } ?? "HTTP \(response?.statusCode ?? -1)")
            }
            return
        }
        if response.statusCode == 206 {
            if let range = response.value(forHTTPHeaderField: "Content-Range"), let slash = range.lastIndex(of: "/"),
               let total = Int64(range[range.index(after: slash)...]) {
                size = total
            }
            chunks[index] = data
        } else {
            // A server that ignores the range sends the whole file.
            size = Int64(data.count)
            let from = Int(min(start, size))
            chunks[index] = data.subdata(in: from..<Int(min(start + Self.chunkSize, size)))
        }
    }
}

/// FFmpeg's I/O callbacks over a RangeReader passed as the opaque pointer.
let rangeReaderRead: @convention(c) (UnsafeMutableRawPointer?, UnsafeMutablePointer<UInt8>?, Int32) -> Int32 = { opaque, buffer, size in
    guard let opaque, let buffer else { return flow_averror_eio() }
    return Unmanaged<RangeReader>.fromOpaque(opaque).takeUnretainedValue().read(into: buffer, count: Int(size))
}

let rangeReaderSeek: @convention(c) (UnsafeMutableRawPointer?, Int64, Int32) -> Int64 = { opaque, offset, whence in
    guard let opaque else { return -1 }
    return Unmanaged<RangeReader>.fromOpaque(opaque).takeUnretainedValue().seek(offset: offset, whence: whence)
}
