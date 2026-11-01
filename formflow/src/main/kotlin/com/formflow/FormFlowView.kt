package com.formflow

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.browser.customtabs.CustomTabsIntent
import org.json.JSONObject

/** A WebView showing a FormFlow form. Call [load] with a config; events arrive on [listener] on the main thread. */
@SuppressLint("SetJavaScriptEnabled")
class FormFlowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var listener: FormFlowListener? = null
    private val main = Handler(Looper.getMainLooper())
    private var config: FormFlowConfig? = null
    private var ready = false
    private val timeout = Runnable { fireTimeout() }

    private val web = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        settings.userAgentString = settings.userAgentString + " " + FORMFLOW_USER_AGENT_SUFFIX
        overScrollMode = OVER_SCROLL_NEVER
        setBackgroundColor(0)
        addJavascriptInterface(Bridge(), "FormFlowAndroid")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (config?.isAllowed(url) != false) return false
                if (request.url.scheme == "https" || request.url.scheme == "http") {
                    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }
                }
                return true
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { listener?.dispatch(FormFlowEvent.Error(error.description.toString())); fireTimeout() }
            }
        }
    }

    init { addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

    private inner class Bridge {
        @JavascriptInterface
        fun postMessage(json: String) {
            val e = FormFlowEvent.parse(json) ?: return
            main.post {
                if (e is FormFlowEvent.Ready) { ready = true; main.removeCallbacks(timeout) }
                if (e is FormFlowEvent.Request) listener?.onRequest(e.request) { r -> respond(e.request.requestId, r) }
                listener?.dispatch(e)
            }
        }
    }

    private fun fireTimeout() { if (!ready) { ready = true; main.removeCallbacks(timeout); listener?.onTimeout() } }

    /** Load the form. Call early (before it is shown) to preload. */
    fun load(config: FormFlowConfig, restart: Boolean = false) {
        this.config = config
        ready = false
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, config.timeoutMs)
        web.loadUrl(config.url(restart))
    }

    /** Start over, ignoring saved progress. */
    fun restart() { config?.let { load(it, restart = true) } }

    /** Warm the WebView with the form without showing it, then add the view to your layout when the step comes. */
    fun preload(config: FormFlowConfig) = load(config)

    /** Answer a `request` event. result may be a String, Number, Boolean, List, Map or null. */
    fun respond(requestId: String, result: Any?) {
        val msg = JSONObject().put("type", "respond").put("requestId", requestId).put("result", JSONObject.wrap(result) ?: JSONObject.NULL).toString()
        web.post { web.evaluateJavascript("window.FormFlow&&window.FormFlow.native&&window.FormFlow.native.receive(${JSONObject.quote(msg)});", null) }
    }

    fun destroy() { main.removeCallbacks(timeout); web.removeJavascriptInterface("FormFlowAndroid"); web.destroy() }
}
