package com.example.trnberechnung.warnings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object OfficialUrlPolicy {
    private val allowedHosts =
        setOf(
            "bsh.de",
            "www.bsh.de",
            "www2.bsh.de",
            "elwis.de",
            "www.elwis.de",
        )

    fun validatedUrl(rawUrl: String): HttpUrl? {
        val url = rawUrl.trim().toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.port != 443) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val host = url.host.lowercase()
        if (host !in allowedHosts) return null
        return url
    }

    fun isAllowed(rawUrl: String): Boolean = validatedUrl(rawUrl) != null

    fun linkKind(rawUrl: String): OfficialLinkKind? {
        val url = validatedUrl(rawUrl) ?: return null
        return if (url.encodedPath.lowercase().endsWith(".pdf")) {
            OfficialLinkKind.PDF
        } else {
            OfficialLinkKind.WEB
        }
    }
}

sealed interface OfficialLinkOpenResult {
    data object Opened : OfficialLinkOpenResult

    data class Error(
        val userMessage: String,
    ) : OfficialLinkOpenResult
}

object OfficialLinkOpener {
    fun open(
        context: Context,
        rawUrl: String,
    ): OfficialLinkOpenResult {
        val validated =
            OfficialUrlPolicy.validatedUrl(rawUrl)
                ?: return OfficialLinkOpenResult.Error(
                    "Der amtliche Link ist ungültig oder nicht freigegeben.",
                )
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(validated.toString())).apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        return try {
            context.startActivity(intent)
            OfficialLinkOpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            OfficialLinkOpenResult.Error(
                "Auf diesem Gerät ist keine App zum Öffnen der amtlichen Quelle verfügbar.",
            )
        } catch (_: SecurityException) {
            OfficialLinkOpenResult.Error("Die amtliche Quelle konnte nicht sicher geöffnet werden.")
        } catch (_: RuntimeException) {
            OfficialLinkOpenResult.Error("Die amtliche Quelle konnte nicht geöffnet werden.")
        }
    }
}
