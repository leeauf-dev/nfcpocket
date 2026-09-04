package dev.leeauf.nfcpocket.model

import org.json.JSONObject
import java.util.UUID

enum class NfcType(val label: String) {
    URL("URL"),
    PHONE("Téléphone"),
    SMS("SMS"),
    EMAIL("Email"),
    LOCATION("Localisation"),
    TEXT("Texte"),
    CONTACT("Contact")
}

sealed interface NfcPayload {
    data class Url(val url: String) : NfcPayload
    data class Phone(val number: String) : NfcPayload
    data class Sms(val number: String, val message: String = "") : NfcPayload
    data class Email(val recipient: String, val subject: String = "", val body: String = "") : NfcPayload
    data class Location(val latitude: String, val longitude: String) : NfcPayload
    data class Text(val text: String) : NfcPayload
    data class Contact(val name: String, val phone: String = "", val email: String = "") : NfcPayload
}

data class NfcItem(
    val id: String = UUID.randomUUID().toString(),
    val type: NfcType,
    val title: String,
    val payload: NfcPayload,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
) {
    fun preview(): String = when (val value = payload) {
        is NfcPayload.Url -> value.url
        is NfcPayload.Phone -> value.number
        is NfcPayload.Sms -> listOf(value.number, value.message).filter { it.isNotBlank() }.joinToString(" · ")
        is NfcPayload.Email -> listOf(value.recipient, value.subject).filter { it.isNotBlank() }.joinToString(" · ")
        is NfcPayload.Location -> "${value.latitude}, ${value.longitude}"
        is NfcPayload.Text -> value.text
        is NfcPayload.Contact -> listOf(value.name, value.phone, value.email).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("favorite", favorite)
        put("createdAt", createdAt)
        if (lastUsedAt != null) put("lastUsedAt", lastUsedAt)
        put("payload", payload.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): NfcItem {
            val type = NfcType.valueOf(json.getString("type"))
            return NfcItem(
                id = json.getString("id"),
                type = type,
                title = json.optString("title", type.label),
                payload = payloadFromJson(type, json.getJSONObject("payload")),
                favorite = json.optBoolean("favorite", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                lastUsedAt = if (json.has("lastUsedAt")) json.optLong("lastUsedAt") else null
            )
        }
    }
}

private fun NfcPayload.toJson() = JSONObject().apply {
    when (val value = this@toJson) {
        is NfcPayload.Url -> put("url", value.url)
        is NfcPayload.Phone -> put("number", value.number)
        is NfcPayload.Sms -> {
            put("number", value.number)
            put("message", value.message)
        }
        is NfcPayload.Email -> {
            put("recipient", value.recipient)
            put("subject", value.subject)
            put("body", value.body)
        }
        is NfcPayload.Location -> {
            put("latitude", value.latitude)
            put("longitude", value.longitude)
        }
        is NfcPayload.Text -> put("text", value.text)
        is NfcPayload.Contact -> {
            put("name", value.name)
            put("phone", value.phone)
            put("email", value.email)
        }
    }
}

private fun payloadFromJson(type: NfcType, json: JSONObject): NfcPayload = when (type) {
    NfcType.URL -> NfcPayload.Url(json.optString("url"))
    NfcType.PHONE -> NfcPayload.Phone(json.optString("number"))
    NfcType.SMS -> NfcPayload.Sms(json.optString("number"), json.optString("message"))
    NfcType.EMAIL -> NfcPayload.Email(
        json.optString("recipient"), json.optString("subject"), json.optString("body")
    )
    NfcType.LOCATION -> NfcPayload.Location(json.optString("latitude"), json.optString("longitude"))
    NfcType.TEXT -> NfcPayload.Text(json.optString("text"))
    NfcType.CONTACT -> NfcPayload.Contact(
        json.optString("name"), json.optString("phone"), json.optString("email")
    )
}
