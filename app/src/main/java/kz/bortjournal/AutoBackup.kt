package kz.bortjournal

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.OpenableColumns
import org.json.JSONObject

/** Пишет копию данных в файл, выбранный пользователем (Google Диск, папка и т.п.). */
object AutoBackup {
    private const val PREFS = "autobackup"
    private val thread = HandlerThread("autobackup").apply { start() }
    private val handler = Handler(thread.looper)
    private var pending: String? = null

    fun setUri(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { /* провайдер может не давать постоянный доступ */ }
        var name = "bortjournal-autobackup.json"
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
        } catch (e: Exception) { }
        prefs(ctx).edit().putString("uri", uri.toString()).putString("name", name).remove("error").apply()
    }

    fun disable(ctx: Context) {
        val u = prefs(ctx).getString("uri", null)
        if (u != null) {
            try {
                ctx.contentResolver.releasePersistableUriPermission(
                    Uri.parse(u),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { }
        }
        prefs(ctx).edit().clear().apply()
    }

    fun info(ctx: Context): String {
        val p = prefs(ctx)
        val u = p.getString("uri", null) ?: return ""
        return JSONObject()
            .put("uri", u)
            .put("name", p.getString("name", ""))
            .put("last", p.getLong("last", 0L).takeIf { it > 0 } ?: JSONObject.NULL)
            .put("error", p.getString("error", null) ?: JSONObject.NULL)
            .toString()
    }

    /** Запись с задержкой: частые сохранения склеиваются в одну запись. */
    fun schedule(ctx: Context, json: String, delayMs: Long = 4000) {
        val app = ctx.applicationContext
        if (prefs(app).getString("uri", null) == null) return
        synchronized(this) { pending = json }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ flush(app) }, delayMs)
    }

    private fun flush(ctx: Context) {
        val json = synchronized(this) { pending.also { pending = null } } ?: return
        val u = prefs(ctx).getString("uri", null) ?: return
        try {
            ctx.contentResolver.openOutputStream(Uri.parse(u), "wt")?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("нет доступа к файлу")
            prefs(ctx).edit().putLong("last", System.currentTimeMillis()).remove("error").apply()
        } catch (e: Exception) {
            prefs(ctx).edit().putString("error", e.message ?: e.javaClass.simpleName).apply()
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
