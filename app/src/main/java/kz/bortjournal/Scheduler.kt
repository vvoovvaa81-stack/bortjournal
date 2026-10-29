package kz.bortjournal

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray

object Scheduler {
    private const val PREFS = "scheduler"
    private const val KEY_JSON = "json"
    private const val KEY_COUNT = "count"
    private const val BASE = 1000

    @Synchronized
    fun replaceAll(ctx: Context, json: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val old = prefs.getInt(KEY_COUNT, 0)
        for (i in 0 until old) {
            val pi = PendingIntent.getBroadcast(
                ctx, BASE + i, Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
        var count = 0
        try {
            val arr = JSONArray(json)
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val at = o.optLong("at")
                if (at <= now) continue
                val intent = Intent(ctx, AlarmReceiver::class.java)
                    .putExtra("title", o.optString("title"))
                    .putExtra("text", o.optString("text"))
                    .putExtra("nid", o.optString("id").hashCode())
                val pi = PendingIntent.getBroadcast(
                    ctx, BASE + count, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                count++
            }
        } catch (e: Exception) {
            // bad json: keep whatever was scheduled
        }
        prefs.edit().putString(KEY_JSON, json).putInt(KEY_COUNT, count).apply()
    }

    fun restore(ctx: Context) {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null) ?: return
        replaceAll(ctx, json)
    }
}
