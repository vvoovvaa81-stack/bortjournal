package kz.bortjournal

import android.content.Context
import java.io.File

object Storage {
    private const val NAME = "data.json"

    @Synchronized
    fun load(ctx: Context): String {
        val f = File(ctx.filesDir, NAME)
        return if (f.exists()) f.readText(Charsets.UTF_8) else ""
    }

    @Synchronized
    fun save(ctx: Context, json: String) {
        val tmp = File(ctx.filesDir, "$NAME.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        val target = File(ctx.filesDir, NAME)
        if (!tmp.renameTo(target)) {
            target.writeText(json, Charsets.UTF_8)
            tmp.delete()
        }
    }
}
