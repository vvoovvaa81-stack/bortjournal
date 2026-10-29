package kz.bortjournal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Бортжурнал"
        val text = intent.getStringExtra("text") ?: ""
        val id = intent.getIntExtra("nid", 1)
        val action = intent.getStringExtra("action")?.takeIf { it.isNotEmpty() }
        Notifier.show(context, id, title, text, action)
    }
}
