package dev.auriya.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.auriya.app.data.stats.AutoRecordPrefs
import dev.auriya.app.data.stats.BenchmarkRecorder

class AuriyaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "dev.auriya.app.ACTION_GAME_ENTER" -> {
                val pkg = intent.getStringExtra("pkg") ?: return
                val mode = intent.getStringExtra("mode") ?: "Performance"
                val message = intent.getStringExtra("message") ?: "$pkg: $mode"

                // 1. Show Toast
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                // 2. Check Auto Record
                val autoRecordPrefs = AutoRecordPrefs(context)
                if (autoRecordPrefs.isAutoRecordEnabled(pkg)) {
                    val recorder = BenchmarkRecorder.getInstance(context)
                    if (!recorder.isRecording.value) {
                        recorder.startManualRecording(pkg, mode.lowercase())
                    }
                }
            }

            "dev.auriya.app.ACTION_GAME_EXIT" -> {
                // Finalize Auto Record if recording
                val recorder = BenchmarkRecorder.getInstance(context)
                if (recorder.isRecording.value) {
                    recorder.stopManualRecording()
                }
            }

            "dev.auriya.app.ACTION_SHOW_TOAST", "dev.auriya.app.TOAST" -> {
                val message = intent.getStringExtra("message") ?: "Auriya: Tweaks applied"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
