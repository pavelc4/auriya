package dev.auriya.app.data

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

data class RootFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val lastModified: Long,
)

/**
 * Thin facade over libsu so the rest of the app does not import
 * `com.topjohnwu.superuser.*` directly. Centralising here keeps the
 * "what runs as root" surface small and reviewable.
 */
object RootShell {
    private const val TAG = "AuriyaRoot"

    /** Active check — closes any cached non-root shell first, then opens a fresh
     *  root shell (may show SU prompt). Avoids getting stuck with a cached non-root shell. */
    fun hasRoot(): Boolean {
        val cached = Shell.getCachedShell()
        Log.d(TAG, "hasRoot() called | cached=$cached | cachedIsRoot=${cached?.isRoot}")
        // If there's a cached shell that isn't root, close it so libsu builds a fresh one.
        if (cached != null && !cached.isRoot) {
            Log.d(TAG, "hasRoot() closing non-root cached shell")
            cached.close()
        }
        return try {
            val shell = Shell.getShell()
            val result = shell.isRoot
            Log.d(TAG, "hasRoot() Shell.getShell() returned isRoot=$result shell=$shell")
            result
        } catch (e: Throwable) {
            Log.e(TAG, "hasRoot() exception: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** Passive check — only reads the already-open cached shell, never prompts. */
    fun hasCachedRoot(): Boolean {
        val cached = Shell.getCachedShell()
        val result = cached?.isRoot ?: false
        Log.d(TAG, "hasCachedRoot() cached=$cached isRoot=$result")
        return result
    }

    fun run(cmd: String): String {
        val res = Shell.cmd(cmd).exec()
        return res.out.joinToString("\n").trim()
    }

    /** Run a shell command capturing both stdout and stderr. */
    fun runWithStderr(cmd: String): String {
        val out = mutableListOf<String>()
        Shell.cmd(cmd).to(out, out).exec()
        return out.joinToString("\n").trim()
    }

    /** Run a shell command and return exit code; ignores output. */
    fun exec(cmd: String): Int {
        val res = Shell.cmd(cmd).exec()
        return if (res.isSuccess) 0 else res.code
    }

    /** List entries inside [dirPath] with root visibility. */
    fun listEntries(dirPath: String): List<RootFileEntry> =
        runCatching {
            SuFile(dirPath)
                .listFiles()
                ?.map { file ->
                    RootFileEntry(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        lastModified = file.lastModified(),
                    )
                }.orEmpty()
        }.getOrDefault(emptyList())

    /** Read up to [maxBytes] of [path] as raw bytes (handles virtual proc/sys files). */
    fun readBytes(
        path: String,
        maxBytes: Int = 16 * 1024 * 1024,
    ): ByteArray? =
        try {
            SuFileInputStream.open(SuFile(path)).use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (total < maxBytes) {
                    val toRead = minOf(buf.size, maxBytes - total)
                    val read = input.read(buf, 0, toRead)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    total += read
                }
                out.toByteArray()
            }
        } catch (_: Throwable) {
            null
        }

    /** Open an output stream with root write access, ensuring parent directories exist. */
    fun openOutputStream(path: String): OutputStream {
        val targetFile = SuFile(path)
        targetFile.parentFile?.mkdirs()
        return SuFileOutputStream.open(targetFile)
    }

    /** Read the full contents of [path] as UTF-8 (root file access). */
    fun readText(path: String): String? =
        try {
            SuFileInputStream.open(SuFile(path)).bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        }

    /** Read the last [tailLines] lines of [path]. Cheaper than full read. */
    fun tail(
        path: String,
        tailLines: Int = 100,
    ): String = run("tail -n $tailLines '$path' 2>/dev/null")

    /** True when the file exists (with root visibility). */
    fun exists(path: String): Boolean = SuFile(path).exists()

    /** Atomic-ish write: write tmp, then `mv`. Caller responsible for chmod. */
    fun writeText(
        path: String,
        content: String,
    ): Boolean =
        try {
            val targetFile = SuFile(path)
            targetFile.parentFile?.mkdirs()
            val tmp = "$path.tmp.${System.currentTimeMillis()}.${(1000..9999).random()}"
            SuFileOutputStream.open(SuFile(tmp)).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            run("mv '$tmp' '$path' && chmod 0644 '$path'")
            true
        } catch (_: Throwable) {
            false
        }
}
