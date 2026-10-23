package dev.auriya.app.ui.record.panes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.auriya.app.data.stats.BenchmarkRecorder
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.record.components.formatDuration
import java.io.File

/**
 * Direct CSV Export to Download/auriya/ without popping up the share sheet
 */
fun exportSessionCsvDirect(
    context: Context,
    session: BenchmarkSession,
) {
    runCatching {
        val safeName = session.appLabel.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "auriya_benchmark_${safeName}_${session.startTimeEpoch}.csv"

        BenchmarkRecorder.saveSessionToDownloadAuriya(context, session)
        Toast.makeText(context, "Saved to Download/auriya/$fileName", Toast.LENGTH_LONG).show()
    }.onFailure { e ->
        e.printStackTrace()
        Toast.makeText(context, "Failed to export: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Open Android system share sheet for sharing CSV file to other apps
 */
fun shareSessionCsv(
    context: Context,
    session: BenchmarkSession,
) {
    runCatching {
        val safeName = session.appLabel.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "auriya_benchmark_${safeName}_${session.startTimeEpoch}.csv"

        val exportFile = File(context.cacheDir, fileName)
        val savedFile = BenchmarkRecorder.saveSessionToDownloadAuriya(context, session)
        if (!exportFile.exists() && savedFile != null && savedFile.exists()) {
            savedFile.copyTo(exportFile, overwrite = true)
        }

        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile,
            )

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Auriya Benchmark Report: ${session.appLabel}")
                putExtra(Intent.EXTRA_TEXT, "Auriya Benchmark Report for ${session.appLabel}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        context.startActivity(Intent.createChooser(intent, "Share CSV Report with..."))
    }.onFailure { e ->
        e.printStackTrace()
        Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Copies a formatted Markdown benchmark report to the clipboard
 */
fun copyMarkdownReport(
    context: Context,
    session: BenchmarkSession,
) {
    val displayJank =
        if (session.samples.isNotEmpty()) {
            session.samples.maxOfOrNull { it.jank } ?: session.totalJank
        } else {
            session.totalJank
        }

    val report =
        buildString {
            appendLine("### Auriya Benchmark Report: **${session.appLabel}**")
            appendLine("- **Package:** `${session.packageName}`")
            appendLine("- **Profile:** `${session.profile}`")
            appendLine("- **Duration:** ${formatDuration(session.durationSeconds)} (${session.samplesCount} samples)")
            appendLine("- **Average FPS:** **%.1f FPS**".format(session.avgFps))
            appendLine("- **1% Low FPS:** **%.1f FPS**".format(session.minLow1Pct))
            appendLine("- **Peak FPS:** **%.1f FPS**".format(session.maxFps))
            appendLine("- **Jank Stutter:** $displayJank frames")
            appendLine("- **Avg CPU Load:** %.1f%%".format(session.avgCpuLoad))
            if (session.maxCpuTemp != null) appendLine("- **Peak CPU Temp:** %.1f°C".format(session.maxCpuTemp))
            if (session.maxBatteryTemp != null) appendLine("- **Peak Battery Temp:** %.1f°C".format(session.maxBatteryTemp))
            appendLine()
            appendLine("*Captured with Auriya Daemon v2.0*")
        }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Auriya Benchmark Report", report))
    Toast.makeText(context, "Benchmark report copied to clipboard!", Toast.LENGTH_SHORT).show()
}
