package dev.auriya.app.data

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream

/**
 * Thin facade over libsu so the rest of the app does not import
 * `com.topjohnwu.superuser.*` directly. Centralising here keeps the
 * "what runs as root" surface small and reviewable.
 */
object RootShell {
    fun hasRoot(): Boolean = Shell.getShell().isRoot

    /** Run a shell command and return its stdout (joined with \n). */
    fun run(cmd: String): String {
        val res = Shell.cmd(cmd).exec()
        return res.out.joinToString("\n").trim()
    }

    /** Run a shell command and return exit code; ignores output. */
    fun exec(cmd: String): Int {
        val res = Shell.cmd(cmd).exec()
        return if (res.isSuccess) 0 else res.code
    }

    /** Read the full contents of [path] as UTF-8 (root file access). */
    fun readText(path: String): String? = try {
        SuFileInputStream.open(SuFile(path)).bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
        null
    }

    /** Read the last [tailLines] lines of [path]. Cheaper than full read. */
    fun tail(path: String, tailLines: Int = 100): String =
        run("tail -n $tailLines '$path' 2>/dev/null")

    /** True when the file exists (with root visibility). */
    fun exists(path: String): Boolean = SuFile(path).exists()

    /** Atomic-ish write: write tmp, then `mv`. Caller responsible for chmod. */
    fun writeText(path: String, content: String): Boolean = try {
        val tmp = "$path.tmp.${System.currentTimeMillis()}"
        SuFileOutputStream.open(SuFile(tmp)).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        run("mv '$tmp' '$path' && chmod 0644 '$path'")
        true
    } catch (_: Throwable) {
        false
    }
}
