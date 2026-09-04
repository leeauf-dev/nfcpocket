package com.leeauf.pocketnfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.leeauf.pocketnfc.nfc.NfcEmulationController
import com.leeauf.pocketnfc.ui.AppViewModel
import com.leeauf.pocketnfc.ui.NfcPocketApp
import com.leeauf.pocketnfc.ui.NfcStatus
import com.leeauf.pocketnfc.ui.theme.NfcPocketTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()
    private var shareText by mutableStateOf<String?>(null)
    private var shareGeneration by mutableIntStateOf(0)
    private var clipboardCheckGeneration by mutableIntStateOf(0)
    private var nfcStatus by mutableStateOf(NfcStatus.UNAVAILABLE)

    private val readReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NfcEmulationController.ACTION_NDEF_READ) return
            val count = intent.getIntExtra(NfcEmulationController.EXTRA_READ_COUNT, 0)
            viewModel.notifyRead(count)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateNfcStatus()
        viewModel.refreshActiveSession()
        acceptShareIntent(intent)
        ContextCompat.registerReceiver(
            this,
            readReceiver,
            IntentFilter(NfcEmulationController.ACTION_NDEF_READ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setContent {
            NfcPocketTheme {
                NfcPocketApp(
                    viewModel = viewModel,
                    nfcStatus = nfcStatus,
                    sharedText = shareText,
                    shareGeneration = shareGeneration,
                    clipboardCheckGeneration = clipboardCheckGeneration,
                    onShareHandled = { shareText = null },
                    onOpenNfcSettings = {
                        runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                            .onFailure { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                    },
                    onPreferredService = { NfcEmulationController.setPreferredService(this, it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNfcStatus()
        viewModel.refreshActiveSession()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) clipboardCheckGeneration++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShareIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(readReceiver) }
        NfcEmulationController.setPreferredService(this, false)
        super.onDestroy()
    }

    private fun acceptShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.toString()
            ?: intent.data?.toString()
        if (!text.isNullOrBlank()) {
            shareText = text
            shareGeneration++
        }
    }

    private fun updateNfcStatus() {
        nfcStatus = when {
            !packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) -> NfcStatus.HCE_UNSUPPORTED
            NfcAdapter.getDefaultAdapter(this) == null -> NfcStatus.UNAVAILABLE
            NfcAdapter.getDefaultAdapter(this)?.isEnabled != true -> NfcStatus.DISABLED
            else -> NfcStatus.AVAILABLE
        }
    }
}
