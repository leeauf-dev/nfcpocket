package com.leeauf.pocketnfc.nfc

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import kotlin.math.min

class NdefHostApduService : HostApduService() {
    private enum class SelectedFile { NONE, CAPABILITY_CONTAINER, NDEF }

    private var applicationSelected = false
    private var selectedFile = SelectedFile.NONE
    private var readReported = false

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) return SW_WRONG_LENGTH

        val message = NfcEmulationController.activeMessage(this) ?: return SW_FILE_NOT_FOUND
        if (isSelectApplication(commandApdu)) {
            applicationSelected = true
            selectedFile = SelectedFile.NONE
            readReported = false
            return SW_SUCCESS
        }
        if (!applicationSelected) return SW_CONDITIONS_NOT_SATISFIED

        selectedFile(commandApdu)?.let { file ->
            selectedFile = file
            return SW_SUCCESS
        }
        if (isSelect(commandApdu)) return SW_FILE_NOT_FOUND
        if (isReadBinary(commandApdu)) return readBinary(commandApdu, message)
        return SW_INS_NOT_SUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        applicationSelected = false
        selectedFile = SelectedFile.NONE
        readReported = false
    }

    private fun readBinary(command: ByteArray, ndef: ByteArray): ByteArray {
        val file = when (selectedFile) {
            SelectedFile.CAPABILITY_CONTAINER -> CAPABILITY_CONTAINER
            SelectedFile.NDEF -> byteArrayOf((ndef.size ushr 8).toByte(), ndef.size.toByte()) + ndef
            SelectedFile.NONE -> return SW_CONDITIONS_NOT_SATISFIED
        }
        if (command.size < 5) return SW_WRONG_LENGTH
        val offset = ((command[2].toInt() and 0x7F) shl 8) or (command[3].toInt() and 0xFF)
        if (offset > file.size) return SW_WRONG_PARAMETERS
        val requested = (command[4].toInt() and 0xFF).let { if (it == 0) 256 else it }
        val end = min(offset + requested, file.size)
        if (selectedFile == SelectedFile.NDEF && end > 2 && !readReported) reportRead()
        return file.copyOfRange(offset, end) + SW_SUCCESS
    }

    private fun reportRead() {
        readReported = true
        sendBroadcast(Intent(NfcEmulationController.ACTION_NDEF_READ).setPackage(packageName))
    }

    private fun selectedFile(command: ByteArray): SelectedFile? {
        if (!isSelect(command) || command.size < 7 || (command[4].toInt() and 0xFF) != 2) return null
        val id = ((command[5].toInt() and 0xFF) shl 8) or (command[6].toInt() and 0xFF)
        return when (id) {
            CC_FILE_ID -> SelectedFile.CAPABILITY_CONTAINER
            NDEF_FILE_ID -> SelectedFile.NDEF
            else -> null
        }
    }

    private fun isSelectApplication(command: ByteArray): Boolean {
        if (!isSelect(command) || command.size < 12 || command[2] != 0x04.toByte()) return false
        val length = command[4].toInt() and 0xFF
        return length == NDEF_AID.size && command.copyOfRange(5, 5 + length).contentEquals(NDEF_AID)
    }

    private fun isSelect(command: ByteArray) =
        command.size >= 4 && command[0] == 0x00.toByte() && command[1] == 0xA4.toByte()

    private fun isReadBinary(command: ByteArray) =
        command.size >= 4 && command[0] == 0x00.toByte() && command[1] == 0xB0.toByte()

    companion object {
        private const val CC_FILE_ID = 0xE103
        private const val NDEF_FILE_ID = 0xE104
        private val NDEF_AID = hex("D2760000850101")
        private val CAPABILITY_CONTAINER = hex("000F20003B00340406E1047FFF00FF")
        private val SW_SUCCESS = hex("9000")
        private val SW_FILE_NOT_FOUND = hex("6A82")
        private val SW_WRONG_LENGTH = hex("6700")
        private val SW_WRONG_PARAMETERS = hex("6B00")
        private val SW_CONDITIONS_NOT_SATISFIED = hex("6985")
        private val SW_INS_NOT_SUPPORTED = hex("6D00")

        private fun hex(value: String): ByteArray = value.chunked(2)
            .map { it.toInt(16).toByte() }.toByteArray()
    }
}
