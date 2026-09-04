package com.leeauf.pocketnfc.util

import android.net.Uri

object UrlUtils {
    private val embeddedUrl = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val domain = Regex(
        "^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}(?::[0-9]{1,5})?(?:[/][^\\s]*)?$",
        RegexOption.IGNORE_CASE
    )

    fun extract(text: String): String? {
        val trimmed = text.trim()
        val candidate = embeddedUrl.find(trimmed)?.value ?: trimmed
        return normalize(candidate)
    }

    fun normalize(input: String): String? {
        var candidate = input.trim().trimEnd('.', ',', ';', ')', ']')
        if (candidate.isBlank() || candidate.any { it.isWhitespace() }) return null
        if (!candidate.startsWith("http://", true) && !candidate.startsWith("https://", true)) {
            if (!domain.matches(candidate)) return null
            candidate = "https://$candidate"
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        val validScheme = uri.scheme.equals("http", true) || uri.scheme.equals("https", true)
        return candidate.takeIf { validScheme && !uri.host.isNullOrBlank() }
    }

    fun defaultTitle(url: String): String = Uri.parse(url).host
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
        ?: "Link"
}
