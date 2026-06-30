package dev.auriya.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AuriyaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "dev.auriya.app.ACTION_SHOW_TOAST") {
            val message = intent.getStringExtra("message") ?: "Auriya: Tweaks applied"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
