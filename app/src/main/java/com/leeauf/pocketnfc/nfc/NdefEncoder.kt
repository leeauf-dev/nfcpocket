package com.leeauf.pocketnfc.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.leeauf.pocketnfc.model.NfcItem

object NdefEncoder {
    fun encode(item: NfcItem): ByteArray = NdefMessage(
        arrayOf(NdefRecord.createUri(item.url))
    ).toByteArray().also {
        require(it.size <= MAX_NDEF_SIZE) { "URL trop volumineuse pour l’émulation" }
    }

    private const val MAX_NDEF_SIZE = 0x7FFD
}
