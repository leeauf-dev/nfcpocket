package dev.leeauf.nfcpocket.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import dev.leeauf.nfcpocket.model.NfcItem
import dev.leeauf.nfcpocket.model.NfcPayload
import java.nio.charset.StandardCharsets

object NdefEncoder {
    fun encode(item: NfcItem): ByteArray {
        val record = when (val payload = item.payload) {
            is NfcPayload.Url -> NdefRecord.createUri(payload.url)
            is NfcPayload.Phone -> NdefRecord.createUri("tel:${payload.number}")
            is NfcPayload.Sms -> NdefRecord.createUri(smsUri(payload))
            is NfcPayload.Email -> NdefRecord.createUri(emailUri(payload))
            is NfcPayload.Location -> NdefRecord.createUri("geo:${payload.latitude},${payload.longitude}")
            is NfcPayload.Text -> createTextRecord(payload.text)
            is NfcPayload.Contact -> NdefRecord.createMime(
                "text/vcard",
                vCard(payload).toByteArray(StandardCharsets.UTF_8)
            )
        }
        return NdefMessage(arrayOf(record)).toByteArray().also {
            require(it.size <= MAX_NDEF_SIZE) { "Contenu trop volumineux pour l’émulation" }
        }
    }

    private fun createTextRecord(text: String): NdefRecord {
        val language = "fr".toByteArray(StandardCharsets.US_ASCII)
        val body = text.toByteArray(StandardCharsets.UTF_8)
        val payload = byteArrayOf(language.size.toByte()) + language + body
        return NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
    }

    private fun smsUri(value: NfcPayload.Sms): Uri {
        val query = if (value.message.isBlank()) "" else "?body=${Uri.encode(value.message)}"
        return Uri.parse("sms:${Uri.encode(value.number, "+")}$query")
    }

    private fun emailUri(value: NfcPayload.Email): Uri {
        val parameters = buildList {
            if (value.subject.isNotBlank()) add("subject=${Uri.encode(value.subject)}")
            if (value.body.isNotBlank()) add("body=${Uri.encode(value.body)}")
        }
        val query = parameters.joinToString("&").let { if (it.isBlank()) "" else "?$it" }
        return Uri.parse("mailto:${Uri.encode(value.recipient, "@+")}$query")
    }

    private fun vCard(value: NfcPayload.Contact): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        append("FN:").append(escapeVCard(value.name)).append("\r\n")
        if (value.phone.isNotBlank()) append("TEL;TYPE=CELL:").append(escapeVCard(value.phone)).append("\r\n")
        if (value.email.isNotBlank()) append("EMAIL:").append(escapeVCard(value.email)).append("\r\n")
        append("END:VCARD\r\n")
    }

    private fun escapeVCard(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    private const val MAX_NDEF_SIZE = 0x7FFD
}
