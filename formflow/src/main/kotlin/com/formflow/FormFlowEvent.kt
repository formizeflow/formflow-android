package com.formflow

import org.json.JSONArray
import org.json.JSONObject

data class Outcome(val passed: Boolean?, val outcomeId: String?, val endingId: String?)
/** The form asks the app to do something: notifications, tracking, open a URL, sign in, deep link, or a custom name. */
data class FormFlowRequest(val requestId: String, val action: String, val params: Map<String, String>)

sealed class FormFlowEvent {
    data class Ready(val formId: String, val versionId: String?, val experimentId: String?, val variantId: String?, val locale: String) : FormFlowEvent()
    object View : FormFlowEvent()
    object Start : FormFlowEvent()
    data class Step(val index: Int, val fieldId: String, val total: Int) : FormFlowEvent()
    /** value is present only with includeAnswers and a non-sensitive field. String, Number, Boolean, List or Map. */
    data class Answer(val fieldId: String, val type: String, val value: Any?) : FormFlowEvent()
    data class Complete(val submissionId: String, val outcome: Outcome, val endingId: String?, val durationMs: Long?, val answers: Map<String, Any?>?) : FormFlowEvent()
    data class Request(val request: FormFlowRequest) : FormFlowEvent()
    data class Progress(val step: Int, val total: Int, val percent: Int) : FormFlowEvent()
    object Close : FormFlowEvent()
    data class Error(val message: String) : FormFlowEvent()

    companion object {
        /** Parse a bridge message; null for anything that is not a FormFlow message. `submit` and `resize` are dropped (`complete` follows `submit`). */
        fun parse(json: String): FormFlowEvent? = try {
            val o = JSONObject(json)
            if (o.optString("source") != "formflow") null else {
                val d = o.optJSONObject("data") ?: JSONObject()
                when (o.optString("type")) {
                    "ready" -> Ready(d.optString("formId", o.optString("formId")), d.optStr("versionId"), d.optStr("experimentId"), d.optStr("variantId"), d.optString("locale"))
                    "view" -> View
                    "start" -> Start
                    "step" -> Step(d.getInt("index"), d.optString("fieldId"), d.getInt("total"))
                    "answer" -> Answer(d.getString("fieldId"), d.optString("type", "unknown"), if (d.has("value") && !d.isNull("value")) unwrap(d.get("value")) else null)
                    "complete" -> {
                        val oc = d.optJSONObject("outcome") ?: JSONObject()
                        Complete(d.getString("submissionId"),
                            Outcome(if (oc.has("passed")) oc.optBoolean("passed") else null, oc.optStr("outcomeId"), oc.optStr("endingId")),
                            d.optStr("endingId"), if (d.has("durationMs")) d.optLong("durationMs") else null,
                            d.optJSONObject("answers")?.let { unwrap(it) as Map<String, Any?> })
                    }
                    "request" -> {
                        val p = d.optJSONObject("params")
                        Request(FormFlowRequest(d.getString("requestId"), d.getString("action"), p?.keys()?.asSequence()?.associateWith { p.optString(it) } ?: emptyMap()))
                    }
                    "progress" -> Progress(d.getInt("step"), d.getInt("total"), d.optInt("percent"))
                    "close" -> Close
                    "error" -> Error(d.optString("message"))
                    else -> null
                }
            }
        } catch (e: org.json.JSONException) { null }

        private fun JSONObject.optStr(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null

        private fun unwrap(v: Any?): Any? = when (v) {
            is JSONObject -> v.keys().asSequence().associateWith { unwrap(v.get(it)) }
            is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
            JSONObject.NULL -> null
            else -> v
        }
    }
}

interface FormFlowListener {
    fun onEvent(event: FormFlowEvent) {}
    fun onReady(e: FormFlowEvent.Ready) {}
    fun onStart() {}
    fun onStep(e: FormFlowEvent.Step) {}
    fun onAnswer(e: FormFlowEvent.Answer) {}
    fun onComplete(e: FormFlowEvent.Complete) {}
    fun onClose() {}
    fun onError(message: String) {}
    /** Ready did not arrive in time or the page failed to load. Carry on with your own flow. */
    fun onTimeout() {}
    /** Answer a request by calling respond (now or later); the result goes back to the form. */
    fun onRequest(request: FormFlowRequest, respond: (Any?) -> Unit) {}
}

internal fun FormFlowListener.dispatch(e: FormFlowEvent) {
    when (e) {
        is FormFlowEvent.Ready -> onReady(e)
        FormFlowEvent.Start -> onStart()
        is FormFlowEvent.Step -> onStep(e)
        is FormFlowEvent.Answer -> onAnswer(e)
        is FormFlowEvent.Complete -> onComplete(e)
        FormFlowEvent.Close -> onClose()
        is FormFlowEvent.Error -> onError(e.message)
        else -> Unit
    }
    onEvent(e)
}
