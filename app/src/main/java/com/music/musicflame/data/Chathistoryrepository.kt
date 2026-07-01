package com.music.musicflame.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
class ChatHistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)

    fun saveMessages(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject()
            obj.put("role", msg.role)
            obj.put("text", msg.text)
            array.put(obj)
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    fun loadMessages(): List<ChatMessage> {
        val json = prefs.getString("messages", "[]") ?: "[]"
        val array = JSONArray(json)
        val result = mutableListOf<ChatMessage>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                ChatMessage(
                    role = obj.getString("role"),
                    text = obj.getString("text")
                )
            )
        }
        return result
    }

    fun clearMessages() {
        prefs.edit().remove("messages").apply()
    }
}
