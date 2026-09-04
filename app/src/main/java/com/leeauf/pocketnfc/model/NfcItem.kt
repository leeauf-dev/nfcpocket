package com.leeauf.pocketnfc.model

import org.json.JSONObject
import java.util.UUID

data class NfcItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("url", url)
        put("favorite", favorite)
        put("createdAt", createdAt)
        if (lastUsedAt != null) put("lastUsedAt", lastUsedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): NfcItem {
            // The payload fallback migrates URL entries created by early builds.
            val url = json.optString("url").ifBlank {
                json.optJSONObject("payload")?.optString("url").orEmpty()
            }
            require(url.isNotBlank())
            return NfcItem(
                id = json.getString("id"),
                title = json.optString("title", "Link"),
                url = url,
                favorite = json.optBoolean("favorite", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                lastUsedAt = if (json.has("lastUsedAt")) json.optLong("lastUsedAt") else null
            )
        }
    }
}
