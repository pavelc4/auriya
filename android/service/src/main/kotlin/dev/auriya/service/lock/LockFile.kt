package dev.auriya.service.lock

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Single-instance lock for the companion service.
 *
 * Held for the entire lifetime of the JVM via an exclusive [FileLock].
 * The daemon may inspect this file (e.g. `fcntl(F_GETLK)`) to detect a
 * crashed companion in real time — when the JVM exits or is killed the
 * OS releases the lock automatically.
 */
class LockFile(private val path: File) : AutoCloseable {
    private var raf: RandomAccessFile? = null
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /**
     * Try to acquire the lock. Returns true on success, false if
     * another companion process is already holding it (in which case
     * this instance should exit instead of fighting for the lock).
     */
    fun tryAcquire(): Boolean {
        path.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val r = RandomAccessFile(path, "rw")
        val c = r.channel
        return try {
            val l = c.tryLock()
            if (l == null) {
                c.close()
                r.close()
                false
            } else {
                raf = r
                channel = c
                lock = l
                true
            }
        } catch (_: OverlappingFileLockException) {
            c.close()
            r.close()
            false
        } catch (t: Throwable) {
            c.close()
            r.close()
            throw t
        }
    }

    override fun close() {
        try { lock?.release() } catch (_: Throwable) {}
        try { channel?.close() } catch (_: Throwable) {}
        try { raf?.close() } catch (_: Throwable) {}
        lock = null
        channel = null
        raf = null
    }
}
