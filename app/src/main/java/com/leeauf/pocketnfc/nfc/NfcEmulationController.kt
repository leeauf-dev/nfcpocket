package com.leeauf.pocketnfc.nfc

import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.util.Base64
import com.leeauf.pocketnfc.model.NfcItem
import org.json.JSONObject

data class ActiveNfcSession(
    val item: NfcItem,
    val startedAt: Long,
    val readCount: Int
)

object NfcEmulationController {
    private const val PREFS = "hce_state"
    private const val KEY_NDEF = "ndef"
    private const val KEY_ITEM = "item"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_READ_COUNT = "read_count"
    const val ACTION_NDEF_READ = "com.leeauf.pocketnfc.NDEF_READ"
    const val EXTRA_READ_COUNT = "read_count"

    fun activate(context: Context, item: NfcItem, ndefMessage: ByteArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NDEF, Base64.encodeToString(ndefMessage, Base64.NO_WRAP))
            .putString(KEY_ITEM, item.toJson().toString())
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .putInt(KEY_READ_COUNT, 0)
            .apply()
    }

    fun stop(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun activeMessage(context: Context): ByteArray? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NDEF, null)
            ?: return null
        return runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull()
    }

    fun activeSession(context: Context): ActiveNfcSession? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.contains(KEY_NDEF)) return null
        val item = runCatching {
            NfcItem.fromJson(JSONObject(preferences.getString(KEY_ITEM, null) ?: return null))
        }.getOrNull() ?: return null
        return ActiveNfcSession(
            item = item,
            startedAt = preferences.getLong(KEY_STARTED_AT, System.currentTimeMillis()),
            readCount = preferences.getInt(KEY_READ_COUNT, 0)
        )
    }

    fun recordRead(context: Context): Int {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = preferences.getInt(KEY_READ_COUNT, 0) + 1
        preferences.edit().putInt(KEY_READ_COUNT, next).apply()
        return next
    }

    fun setPreferredService(context: Context, preferred: Boolean) {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return
        val cardEmulation = runCatching { CardEmulation.getInstance(adapter) }.getOrNull() ?: return
        val component = ComponentName(context, NdefHostApduService::class.java)
        runCatching {
            if (preferred && context is android.app.Activity) {
                cardEmulation.setPreferredService(context, component)
            } else if (context is android.app.Activity) {
                cardEmulation.unsetPreferredService(context)
            }
        }
    }
}
