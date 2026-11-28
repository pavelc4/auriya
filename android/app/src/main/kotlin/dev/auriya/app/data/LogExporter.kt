package dev.auriya.app.data

import dev.auriya.shared.config.ConfigPaths
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPOutputStream
import kotlin.math.min

internal interface LogSource {
    fun listEntries(dirPath: String): List<RootFileEntry>

    fun exists(path: String): Boolean

    fun readBytes(
        path: String,
        maxBytes: Int = MAX_ENTRY_BYTES,
    ): ByteArray?

    fun runCommand(cmd: String): String?

    fun openOutputStream(path: String): OutputStream

    companion object {
        const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
    }
}

private object RootLogSource : LogSource {
    override fun listEntries(dirPath: String): List<RootFileEntry> = RootShell.listEntries(dirPath)

    override fun exists(path: String): Boolean = RootShell.exists(path)

    override fun readBytes(
        path: String,
        maxBytes: Int,
    ): ByteArray? = RootShell.readBytes(path, maxBytes)

    override fun runCommand(cmd: String): String? =
        RootShell.runWithStderr(cmd).takeIf { it.isNotEmpty() }

    override fun openOutputStream(path: String): OutputStream = RootShell.openOutputStream(path)
}

/**
 * Collects Auriya, KernelSU/KernelSU-Next, kernel, Android crash/system, and
 * hardware diagnostics directly via libsu file APIs and writes a POSIX ustar
 * gzipped archive to [ARCHIVE_PATH].
 */
object LogExporter {
    const val ARCHIVE_PATH: String = "/sdcard/Download/AuriyaLogs.tar.gz"
    private const val ARCHIVE_ROOT: String = "AuriyaLogs"

    private val KSU_LOG_REGEX = Regex("kernelsu|ksud|ksu", RegexOption.IGNORE_CASE)
    private val PROP_FILTER_REGEX =
        Regex(
            "ro\\.product|ro\\.board|ro\\.hardware|ro\\.soc|ro\\.build\\.version|" +
                "ro\\.gfx|vendor\\.hwc|debug\\.hwui|debug\\.renderengine",
            RegexOption.IGNORE_CASE,
        )
    private val WINDOW_FILTER_REGEX = Regex("mCurrentFocus|mFocusedApp|DisplayFrames")
    private val PLATFORM_GPU_REGEX = Regex("gpu|mali|sgpu|exynos", RegexOption.IGNORE_CASE)

    fun export(): Result<String> = exportTo(RootLogSource, ARCHIVE_PATH)

    internal fun exportTo(
        source: LogSource,
        outputPath: String,
    ): Result<String> =
        runCatching {
            TarGzWriter(source.openOutputStream(outputPath)).use { writer ->
                collectManifest(writer, source)
                collectAuriya(writer, source)
                val dmesg = collectKernel(writer, source)
                collectKernelSu(writer, source, dmesg)
                collectAndroidSystem(writer, source)
                collectHardwareMap(writer, source)
            }
            outputPath
        }

    private fun collectManifest(
        writer: TarGzWriter,
        source: LogSource,
    ) {
        val timestampFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        val manifest =
            buildString {
                appendLine("exported_utc=${timestampFormat.format(Date())}")
                source.runCommand("uname -a")?.let { appendLine("uname=$it") }
                source.runCommand("id")?.let { appendLine("uid=$it") }
            }
        writer.addText("manifest.txt", manifest)
    }

    private fun collectAuriya(
        writer: TarGzWriter,
        source: LogSource,
    ) {
        source.readBytes("/data/adb/auriya/daemon.log")?.let { bytes ->
            writer.addBytes("auriya.log", bytes)
        }
        source
            .listEntries("/data/adb/auriya")
            .filter { !it.isDirectory && it.name.startsWith("daemon.log") }
            .forEach { copyFile(source, writer, it.path, "auriya/${it.name}") }

        copyFile(source, writer, "/data/adb/auriya/crash.log", "auriya/crash.log")
        copyFile(source, writer, "/data/adb/auriya/boot_error.log", "auriya/boot_error.log")
        copyFile(source, writer, ConfigPaths.SETTINGS_FILE, "auriya/settings.toml")
        copyFile(source, writer, ConfigPaths.GAMELIST_FILE, "auriya/gamelist.toml")
        copyFile(source, writer, ConfigPaths.CURRENT_PROFILE_FILE, "auriya/current_profile")
        copyFile(source, writer, "/data/adb/modules/auriya/module.prop", "auriya/module.prop")

        val ctl = ConfigPaths.AURIYACTL_BIN
        listOf(
            "info" to "auriya/auriyactl_info.txt",
            "status" to "auriya/auriyactl_status.txt",
            "get-pid" to "auriya/auriyactl_pid.txt",
        ).forEach { (subCmd, targetPath) ->
            source.runCommand("$ctl $subCmd")?.let { output ->
                writer.addText(targetPath, output)
            }
        }
    }

    private fun collectKernel(
        writer: TarGzWriter,
        source: LogSource,
    ): String {
        val dmesg = source.runCommand("dmesg").orEmpty()
        if (dmesg.isNotEmpty()) {
            writer.addText("kernel.log", dmesg)
        }
        copyFile(source, writer, "/proc/last_kmsg", "kernel/last_kmsg.log")
        copyFile(source, writer, "/proc/store_lastkmsg", "kernel/store_lastkmsg.log")
        copyFile(source, writer, "/proc/reset_reason", "kernel/reset_reason.txt")

        source
            .listEntries("/sys/fs/pstore")
            .filter { !it.isDirectory }
            .forEach { copyFile(source, writer, it.path, "kernel/pstore/${it.name}") }

        return dmesg
    }

    private fun collectKernelSu(
        writer: TarGzWriter,
        source: LogSource,
        dmesg: String,
    ) {
        copyRecentFiles(source, writer, "/data/adb/ksu/log", "kernelsu/logs", limit = 6)
        copyFile(source, writer, "/data/adb/ksu/preinit_status", "kernelsu/preinit_status")

        val ksuDmesg =
            dmesg
                .lineSequence()
                .filter { KSU_LOG_REGEX.containsMatchIn(it) }
                .joinToString("\n")
        if (ksuDmesg.isNotEmpty()) {
            writer.addText("kernelsu/ksu_dmesg.log", ksuDmesg)
        }

        val ksuInfo =
            buildString {
                val version =
                    listOf("/data/adb/ksu/bin/ksud", "/data/adb/ksud", "ksud")
                        .firstNotNullOfOrNull { bin -> source.runCommand("$bin --version") }
                appendLine("=== ksud version ===")
                appendLine(version ?: "(ksud not found)")

                appendLine("=== /data/adb/ksu ===")
                source.listEntries("/data/adb/ksu").forEach { entry ->
                    appendLine("${if (entry.isDirectory) "d" else "f"} ${entry.name}")
                }

                appendLine("=== /data/adb/modules ===")
                source
                    .listEntries("/data/adb/modules")
                    .filter { it.isDirectory }
                    .sortedBy { it.name }
                    .forEach { moduleDir ->
                        appendLine("module=${moduleDir.name}")
                        val disabled = source.exists("${moduleDir.path}/disable")
                        if (disabled) appendLine("[disabled]")
                        source.readBytes("${moduleDir.path}/module.prop")?.let { propBytes ->
                            appendLine(propBytes.toString(Charsets.UTF_8).trimEnd())
                        }
                        appendLine()
                    }
            }
        writer.addText("kernelsu/ksu_info.txt", ksuInfo)

        copyFile(source, writer, "/cache/magisk.log", "kernelsu/magisk.log")
        source
            .listEntries("/data/adb/ap/log")
            .filter { !it.isDirectory }
            .forEach { copyFile(source, writer, it.path, "kernelsu/apatch/${it.name}") }
    }

    private fun collectAndroidSystem(
        writer: TarGzWriter,
        source: LogSource,
    ) {
        source.runCommand("logcat -b system,events,crash -d -t 3000")?.let { output ->
            writer.addText("android/logcat_system_events_crash.log", output)
        }

        copyRecentFiles(source, writer, "/data/tombstones", "android/tombstones", limit = 3)
        copyRecentFiles(source, writer, "/data/anr", "android/anr", limit = 3)
        copyRecentFiles(source, writer, "/data/system/dropbox", "android/dropbox", limit = 5)
        copyRecentFiles(source, writer, "/data/misc/logd", "android/logd_persist", limit = 3)

        val windowDisplays = source.runCommand("dumpsys window displays").orEmpty()
        val displayDump = source.runCommand("dumpsys display").orEmpty()
        if (windowDisplays.isNotEmpty() || displayDump.isNotEmpty()) {
            val summary =
                buildString {
                    appendLine("=== window displays ===")
                    windowDisplays
                        .lineSequence()
                        .filter { WINDOW_FILTER_REGEX.containsMatchIn(it) }
                        .forEach { appendLine(it) }
                    appendLine("=== display ===")
                    displayDump
                        .lineSequence()
                        .take(120)
                        .forEach { appendLine(it) }
                }
            writer.addText("android/dumpsys_window_display.txt", summary)
        }

        val devLogs =
            buildString {
                appendLine("=== /dev/log ===")
                source.listEntries("/dev/log").forEach { appendLine(it.name) }
                appendLine("=== /dev/socket/log* ===")
                source
                    .listEntries("/dev/socket")
                    .filter { it.name.startsWith("log") }
                    .forEach { appendLine(it.name) }
            }
        writer.addText("android/sockets_dev_log.txt", devLogs)
    }

    private fun collectHardwareMap(
        writer: TarGzWriter,
        source: LogSource,
    ) {
        source.runCommand("getprop")?.let { props ->
            val filtered =
                props
                    .lineSequence()
                    .filter { PROP_FILTER_REGEX.containsMatchIn(it) }
                    .joinToString("\n")
            writer.addText("hardware/props.txt", filtered)
        }

        val sysfsMap =
            buildString {
                appendSection("devfreq", source.listEntries("/sys/class/devfreq").map { it.name })
                appendSection(
                    "platform gpu",
                    source
                        .listEntries("/sys/devices/platform")
                        .map { it.name }
                        .filter { PLATFORM_GPU_REGEX.containsMatchIn(it) },
                )
                appendSection("kernel gpu", source.listEntries("/sys/kernel/gpu").map { it.name })
                appendSection("kgsl", source.listEntries("/sys/class/kgsl/kgsl-3d0").map { it.name })
                appendSection("drm", source.listEntries("/sys/class/drm").map { it.name })

                appendLine("=== thermal ===")
                source
                    .listEntries("/sys/class/thermal")
                    .filter { it.name.startsWith("thermal_zone") }
                    .sortedBy { it.name }
                    .forEach { zone ->
                        val type =
                            source
                                .readBytes("${zone.path}/type", maxBytes = 1024)
                                ?.toString(Charsets.UTF_8)
                                ?.trim()
                        if (!type.isNullOrEmpty()) {
                            appendLine("${zone.path}: $type")
                        }
                    }

                appendSection(
                    "top-app cpuset",
                    source.listEntries("/dev/cpuset/top-app").map { it.name },
                )
                appendSection(
                    "top-app cgroup",
                    source.listEntries("/sys/fs/cgroup/top-app").map { it.name },
                )

                appendLine("=== bpf ===")
                source
                    .runCommand("mount")
                    ?.lineSequence()
                    ?.filter { it.contains("bpf") }
                    ?.forEach { appendLine(it) }
                source
                    .readBytes("/proc/sys/net/core/bpf_jit_enable", maxBytes = 64)
                    ?.toString(Charsets.UTF_8)
                    ?.trim()
                    ?.let { appendLine("bpf_jit_enable=$it") }
            }
        writer.addText("hardware/sysfs_map.txt", sysfsMap)
    }

    private fun StringBuilder.appendSection(
        title: String,
        entries: List<String>,
    ) {
        appendLine("=== $title ===")
        entries.forEach { appendLine(it) }
    }

    private fun copyFile(
        source: LogSource,
        writer: TarGzWriter,
        srcPath: String,
        archiveRelPath: String,
    ) {
        val bytes = source.readBytes(srcPath) ?: return
        writer.addBytes(archiveRelPath, bytes)
    }

    private fun copyRecentFiles(
        source: LogSource,
        writer: TarGzWriter,
        dirPath: String,
        archiveDir: String,
        limit: Int,
    ) {
        source
            .listEntries(dirPath)
            .filter { !it.isDirectory }
            .sortedByDescending { it.lastModified }
            .take(limit)
            .forEach { entry ->
                copyFile(source, writer, entry.path, "$archiveDir/${entry.name}")
            }
    }

    /**
     * Minimal streaming POSIX ustar .tar.gz writer with zero shell dependencies.
     */
    private class TarGzWriter(
        rawOut: OutputStream,
    ) : AutoCloseable {
        private val gzipOut = GZIPOutputStream(BufferedOutputStream(rawOut))
        private val nowSecs = System.currentTimeMillis() / 1000L

        fun addText(
            relPath: String,
            text: String,
        ) {
            val normalized = if (text.isEmpty() || text.endsWith("\n")) text else "$text\n"
            addBytes(relPath, normalized.toByteArray(Charsets.UTF_8))
        }

        fun addBytes(
            relPath: String,
            data: ByteArray,
        ) {
            val fullPath = "$ARCHIVE_ROOT/${relPath.trimStart('/')}"
            val header = buildUstarHeader(fullPath, data.size.toLong(), nowSecs)
            gzipOut.write(header)
            if (data.isNotEmpty()) {
                gzipOut.write(data)
                val remainder = data.size % TAR_BLOCK_SIZE
                if (remainder != 0) {
                    gzipOut.write(ByteArray(TAR_BLOCK_SIZE - remainder))
                }
            }
        }

        override fun close() {
            gzipOut.write(ByteArray(TAR_BLOCK_SIZE * 2))
            gzipOut.finish()
            gzipOut.close()
        }

        private fun buildUstarHeader(
            path: String,
            size: Long,
            mtimeSecs: Long,
        ): ByteArray {
            val header = ByteArray(TAR_BLOCK_SIZE)
            val pathBytes = path.toByteArray(Charsets.UTF_8)
            if (pathBytes.size <= NAME_FIELD_LEN) {
                pathBytes.copyInto(header, destinationOffset = 0)
            } else {
                val slashIdx =
                    (pathBytes.size - 1 downTo 1).firstOrNull { idx ->
                        pathBytes[idx] == '/'.code.toByte() &&
                            idx <= PREFIX_FIELD_LEN &&
                            (pathBytes.size - idx - 1) <= NAME_FIELD_LEN
                    }
                if (slashIdx != null) {
                    pathBytes.copyInto(header, destinationOffset = PREFIX_OFFSET, endIndex = slashIdx)
                    pathBytes.copyInto(
                        header,
                        destinationOffset = 0,
                        startIndex = slashIdx + 1,
                        endIndex = pathBytes.size,
                    )
                } else {
                    pathBytes.copyInto(header, destinationOffset = 0, endIndex = min(pathBytes.size, NAME_FIELD_LEN))
                }
            }

            writeAscii(header, 100, "0000644\u0000")
            writeAscii(header, 108, "0000000\u0000")
            writeAscii(header, 116, "0000000\u0000")
            writeAscii(header, 124, formatOctal(size, 11) + "\u0000")
            writeAscii(header, 136, formatOctal(mtimeSecs.coerceAtLeast(0L), 11) + "\u0000")
            for (i in 148 until 156) header[i] = ' '.code.toByte()
            header[156] = '0'.code.toByte()
            writeAscii(header, 257, "ustar\u000000")

            var checksum = 0
            for (b in header) checksum += b.toInt() and 0xFF
            writeAscii(header, 148, formatOctal(checksum.toLong(), 6) + "\u0000 ")
            return header
        }

        private fun writeAscii(
            buf: ByteArray,
            offset: Int,
            text: String,
        ) {
            val bytes = text.toByteArray(Charsets.US_ASCII)
            bytes.copyInto(buf, destinationOffset = offset)
        }

        private fun formatOctal(
            value: Long,
            width: Int,
        ): String = value.toString(8).padStart(width, '0')

        companion object {
            private const val TAR_BLOCK_SIZE = 512
            private const val NAME_FIELD_LEN = 100
            private const val PREFIX_OFFSET = 345
            private const val PREFIX_FIELD_LEN = 155
        }
    }
}
