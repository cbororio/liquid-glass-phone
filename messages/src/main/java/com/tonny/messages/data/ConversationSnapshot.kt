package com.tonny.messages.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The conversation list as last loaded, kept in app-private storage so a cold start can show it instantly. */
object ConversationSnapshot {

    private fun file(context: Context) = File(context.noBackupFilesDir, "conversations.json")

    fun read(context: Context): List<Conversation> = runCatching {
        val arr = JSONArray(file(context).readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Conversation(
                threadId = o.getLong("t"),
                address = o.getString("a"),
                name = o.optString("n").takeIf { it.isNotEmpty() },
                photoUri = o.optString("p").takeIf { it.isNotEmpty() },
                snippet = o.getString("s"),
                date = o.getLong("d"),
                unread = o.getBoolean("u"),
            )
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, list: List<Conversation>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("t", c.threadId).put("a", c.address).put("n", c.name.orEmpty()).put("p", c.photoUri.orEmpty())
                        .put("s", c.snippet).put("d", c.date).put("u", c.unread),
                )
            }
            val target = file(context)
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(target)
        }
    }
}
