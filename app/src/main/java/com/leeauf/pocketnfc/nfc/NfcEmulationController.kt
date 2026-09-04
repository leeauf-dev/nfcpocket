package com.leeauf.pocketnfc.nfc

import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.util.Base64

object NfcEmulationController {
    private const val PREFS = "hce_state"
    private const val KEY_NDEF = "ndef"
    const val ACTION_NDEF_READ = "com.leeauf.pocketnfc.NDEF_READ"

    fun activate(context: Context, ndefMessage: ByteArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NDEF, Base64.encodeToString(ndefMessage, Base64.NO_WRAP))
            .apply()
    }

    fun stop(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_NDEF).apply()
    }

    fun activeMessage(context: Context): ByteArray? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NDEF, null)
            ?: return null
        return runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull()
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
