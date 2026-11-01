package com.formflow

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Response of POST /api/v1/public/onboarding/decide. */
data class FormFlowDecision(
    val show: Boolean,
    val experimentId: String?, val variantId: String?, val formId: String?, val slug: String?, val versionId: String?,
    val reason: String,
    /** Set by the SDK when the network failed and a cached decision was returned. */
    val fromCache: Boolean = false,
) {
    /** Config for this decision, or null when the flow must be skipped (holdout, completed, no match). */
    fun config(base: FormFlowConfig): FormFlowConfig? = if (show && slug != null) base.copy(slug = slug, variant = variantId ?: base.variant) else null

    fun toJson(): String = JSONObject().put("show", show).put("experimentId", experimentId).put("variantId", variantId).put("formId", formId)
        .put("slug", slug).put("versionId", versionId).put("reason", reason).toString()

    companion object {
        /** Accepts `{data: {...}}` or the bare object; null for junk. */
        fun parse(json: String): FormFlowDecision? = try {
            val root = JSONObject(json)
            val o = root.optJSONObject("data") ?: root
            if (!o.has("show")) null else FormFlowDecision(o.getBoolean("show"), s(o, "experimentId"), s(o, "variantId"), s(o, "formId"), s(o, "slug"), s(o, "versionId"), o.optString("reason"))
        } catch (e: org.json.JSONException) { null }
        private fun s(o: JSONObject, k: String): String? = if (o.has(k) && !o.isNull(k)) o.optString(k) else null
    }
}

data class DecideRequest(
    val entry: String, val uid: String? = null, val uidSignature: String? = null, val attributes: Map<String, String> = emptyMap(),
    val posthogSessionId: String? = null, val posthogDistinctId: String? = null,
)

class FormFlowClient(context: Context, private val baseUrl: String, private val timeoutMs: Int = 8000) {
    private val prefs = context.applicationContext.getSharedPreferences("formflow", Context.MODE_PRIVATE)

    private fun key(r: DecideRequest) = "decision.${r.entry}.${r.uid ?: "anon"}"
    fun cached(r: DecideRequest): FormFlowDecision? = prefs.getString(key(r), null)?.let { FormFlowDecision.parse(it) }

    internal fun body(r: DecideRequest): String = JSONObject().apply {
        put("entry", r.entry); r.uid?.let { put("uid", it) }; r.uidSignature?.let { put("uid_sig", it) }
        put("attributes", JSONObject(FormFlowConfig(baseUrl, "x", attributes = r.attributes).resolvedAttributes()))
        r.posthogSessionId?.let { put("ph_session", it) }; r.posthogDistinctId?.let { put("ph_distinct", it) }
    }.toString()

    /** Ask the server whether to show the flow. On network failure returns the cached decision (fromCache) or throws. */
    suspend fun decide(r: DecideRequest): FormFlowDecision = withContext(Dispatchers.IO) {
        try {
            val c = URL("${baseUrl.trimEnd('/')}/api/v1/public/onboarding/decide").openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body(r).toByteArray()) }
            if (c.responseCode != 200) throw java.io.IOException("decide ${c.responseCode}")
            val d = FormFlowDecision.parse(c.inputStream.bufferedReader().readText()) ?: throw java.io.IOException("bad decision")
            prefs.edit().putString(key(r), d.toJson()).apply()
            d
        } catch (e: Exception) {
            cached(r)?.copy(fromCache = true) ?: throw e
        }
    }

    /** Warm the decision cache. Never throws. */
    suspend fun preload(r: DecideRequest): FormFlowDecision? = try { decide(r) } catch (e: Exception) { null }
}
