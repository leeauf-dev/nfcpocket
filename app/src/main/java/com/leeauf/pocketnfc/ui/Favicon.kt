package com.leeauf.pocketnfc.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leeauf.pocketnfc.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Composable
fun Favicon(url: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) { bitmap = FaviconCache.load(context, url) }

    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(contentAlignment = Alignment.Center) {
            val icon = bitmap
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(7.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_nfc_material),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private object FaviconCache {
    private const val MAX_BYTES = 256 * 1024
    private const val CACHE_LIFETIME_MS = 7L * 24 * 60 * 60 * 1000
    private const val FAILED_CACHE_LIFETIME_MS = 24L * 60 * 60 * 1000

    suspend fun load(context: Context, pageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val host = runCatching { Uri.parse(pageUrl).host?.lowercase() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val directory = File(context.cacheDir, "favicons").apply { mkdirs() }
        val cacheKey = sha256(host)
        val cacheFile = File(directory, "$cacheKey.ico")
        val missFile = File(directory, "$cacheKey.miss")
        val now = System.currentTimeMillis()
        if (cacheFile.isFile && now - cacheFile.lastModified() < CACHE_LIFETIME_MS) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return@withContext it }
        }
        if (missFile.isFile && now - missFile.lastModified() < FAILED_CACHE_LIFETIME_MS) {
            return@withContext null
        }

        val bytes = download("https://$host/favicon.ico")
        val decoded = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bytes == null || decoded == null) {
            runCatching { missFile.writeText("") }
            return@withContext null
        }
        runCatching {
            cacheFile.writeBytes(bytes)
            missFile.delete()
        }
        decoded
    }

    private fun download(address: String): ByteArray? {
        val connection = (URL(address).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "NFC-Pocket")
            if (connection.responseCode !in 200..299) return null
            val contentLength = connection.contentLength
            if (contentLength > MAX_BYTES) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
