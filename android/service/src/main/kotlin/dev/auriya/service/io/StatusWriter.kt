package dev.auriya.service.io

import dev.auriya.shared.model.SystemStatus
import dev.auriya.shared.status.StatusFormat
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes [SystemStatus] snapshots atomically.
 *
 * The Rust daemon listens for `IN_CLOSE_WRITE` on the parent directory
 * and re-parses the file each time. To avoid the daemon ever seeing a
 * half-written file we follow the standard pattern:
 *
 *   1. Write to a sibling tempfile.
 *   2. fsync the tempfile.
 *   3. `Files.move` with [StandardCopyOption.ATOMIC_MOVE] to swap it in.
 *
 * Atomic move replaces the target file (and its inode) in a single
 * directory operation, so the daemon's read is always either the old
 * snapshot or the new one — never a torn write.
 */
class StatusWriter(private val target: File) {
    private val parent: File = target.parentFile
        ?: error("status file ${target.path} has no parent directory")
    private val tmpName = ".${target.name}.tmp"

    init {
        if (!parent.exists() && !parent.mkdirs()) {
            error("failed to create status directory ${parent.path}")
        }
    }

    /**
     * Render [status] and swap it in. Returns true on success; failures
     * are reported via [onError] so the caller can log without coupling
     * this class to any logging framework.
     */
    fun write(status: SystemStatus, onError: (Throwable) -> Unit = {}): Boolean {
        val tmp = File(parent, tmpName)
        return try {
            val bytes = StatusFormat.encode(status).toByteArray(Charsets.UTF_8)
            tmp.outputStream().use { os ->
                os.write(bytes)
                os.fd.sync()
            }
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // Some filesystems (rare on Android) reject atomic move
                // across mount points. Fall back to plain replace —
                // worst case the daemon may read a transient missing
                // file and retry, which it already handles.
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            true
        } catch (t: Throwable) {
            tmp.delete()
            onError(t)
            false
        }
    }
}
