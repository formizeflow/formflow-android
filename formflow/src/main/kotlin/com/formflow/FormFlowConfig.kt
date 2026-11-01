package com.formflow

import android.os.Build
import java.net.URI
import java.net.URLEncoder

const val FORMFLOW_VERSION = "0.1.0"
const val FORMFLOW_USER_AGENT_SUFFIX = "FormFlowAndroid/$FORMFLOW_VERSION"

enum class ColorScheme(val wire: String) { LIGHT("light"), DARK("dark"), SYSTEM("system") }
data class Appearance(val colorScheme: ColorScheme? = null, val accent: String? = null, val fontFamily: String? = null)

data class FormFlowConfig(
    val baseUrl: String,
    val slug: String,
    val uid: String? = null,
    /** HMAC of uid computed on YOUR server. Never sign in the app. */
    val uidSignature: String? = null,
    val variant: String? = null,
    val locale: String? = null,
    val hidden: Map<String, String> = emptyMap(),
    val prefill: Map<String, String> = emptyMap(),
    /** User attributes (attrs.<key>). Platform, os_version, device_model and locale are added when autoAttributes is on. */
    val attributes: Map<String, String> = emptyMap(),
    val autoAttributes: Boolean = true,
    val appVersion: String? = null,
    val posthogSessionId: String? = null,
    val posthogDistinctId: String? = null,
    /** false starts over instead of resuming saved progress (server-side, per uid). */
    val resume: Boolean? = null,
    val appearance: Appearance? = null,
    val includeAnswers: Boolean = false,
    val showClose: Boolean = false,
    /** Wait for the form to become ready this long before onTimeout fires. */
    val timeoutMs: Long = 8000,
) {
    fun resolvedAttributes(): Map<String, String> {
        val auto = if (autoAttributes) buildMap {
            put("platform", "android"); put("os_version", Build.VERSION.RELEASE ?: ""); put("device_model", Build.MODEL ?: "")
            put("locale", locale ?: java.util.Locale.getDefault().toLanguageTag())
            appVersion?.let { put("app_version", it) }
        } else emptyMap()
        return auto + attributes
    }

    /** `<baseUrl>/embed/<slug>?native=1&uid=&uid_sig=&variant=&lang=&answers=1&close=1&resume=0&ph_*&scheme&accent&font&hidden.x=&prefill.y=&attrs.z=` */
    fun url(restart: Boolean = false): String {
        val q = mutableListOf("native=1")
        if (!uid.isNullOrEmpty()) q += "uid=${enc(uid)}"
        if (!uidSignature.isNullOrEmpty()) q += "uid_sig=${enc(uidSignature)}"
        if (!variant.isNullOrEmpty()) q += "variant=${enc(variant)}"
        if (!locale.isNullOrEmpty()) q += "lang=${enc(locale)}"
        if (includeAnswers) q += "answers=1"
        if (showClose) q += "close=1"
        if (resume == false || restart) q += "resume=0"
        if (!posthogSessionId.isNullOrEmpty()) q += "ph_session=${enc(posthogSessionId)}"
        if (!posthogDistinctId.isNullOrEmpty()) q += "ph_distinct=${enc(posthogDistinctId)}"
        appearance?.colorScheme?.let { q += "scheme=${it.wire}" }
        appearance?.accent?.let { q += "accent=${enc(it)}" }
        appearance?.fontFamily?.let { q += "font=${enc(it)}" }
        for ((k, v) in hidden.toSortedMap()) q += "hidden.${enc(k)}=${enc(v)}"
        for ((k, v) in prefill.toSortedMap()) q += "prefill.${enc(k)}=${enc(v)}"
        for ((k, v) in resolvedAttributes().toSortedMap()) q += "attrs.${enc(k)}=${enc(v)}"
        return "${baseUrl.trimEnd('/')}/embed/${enc(slug)}?${q.joinToString("&")}"
    }

    /** Only the configured host may load inside the WebView. */
    fun isAllowed(url: String): Boolean {
        if (url == "about:blank") return true
        val h = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return h == runCatching { URI(baseUrl).host?.lowercase() }.getOrNull()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
