package com.formflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

/** Compose wrapper. [onRequest] answers form requests (the return value goes back to the form). */
@Composable
fun FormFlowForm(
    config: FormFlowConfig,
    modifier: Modifier = Modifier,
    onReady: (FormFlowEvent.Ready) -> Unit = {},
    onStart: () -> Unit = {},
    onStep: (FormFlowEvent.Step) -> Unit = {},
    onAnswer: (FormFlowEvent.Answer) -> Unit = {},
    onComplete: (FormFlowEvent.Complete) -> Unit = {},
    onClose: () -> Unit = {},
    onError: (String) -> Unit = {},
    onTimeout: () -> Unit = {},
    onRequest: suspend (FormFlowRequest) -> Any? = { null },
) {
    val cb = rememberUpdatedState(arrayOf<Any>(onReady, onStart, onStep, onAnswer, onComplete, onClose, onError, onTimeout, onRequest))
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    AndroidView(modifier = modifier, factory = { ctx ->
        FormFlowView(ctx).also { v ->
            v.listener = object : FormFlowListener {
                @Suppress("UNCHECKED_CAST") override fun onReady(e: FormFlowEvent.Ready) = (cb.value[0] as (FormFlowEvent.Ready) -> Unit)(e)
                @Suppress("UNCHECKED_CAST") override fun onStart() = (cb.value[1] as () -> Unit)()
                @Suppress("UNCHECKED_CAST") override fun onStep(e: FormFlowEvent.Step) = (cb.value[2] as (FormFlowEvent.Step) -> Unit)(e)
                @Suppress("UNCHECKED_CAST") override fun onAnswer(e: FormFlowEvent.Answer) = (cb.value[3] as (FormFlowEvent.Answer) -> Unit)(e)
                @Suppress("UNCHECKED_CAST") override fun onComplete(e: FormFlowEvent.Complete) = (cb.value[4] as (FormFlowEvent.Complete) -> Unit)(e)
                @Suppress("UNCHECKED_CAST") override fun onClose() = (cb.value[5] as () -> Unit)()
                @Suppress("UNCHECKED_CAST") override fun onError(message: String) = (cb.value[6] as (String) -> Unit)(message)
                @Suppress("UNCHECKED_CAST") override fun onTimeout() = (cb.value[7] as () -> Unit)()
                @Suppress("UNCHECKED_CAST") override fun onRequest(request: FormFlowRequest, respond: (Any?) -> Unit) {
                    scope.launch { respond((cb.value[8] as suspend (FormFlowRequest) -> Any?)(request)) }
                }
            }
            v.tag = config.url()
            v.load(config)
        }
    }, update = { v -> val u = config.url(); if (v.tag != u) { v.tag = u; v.load(config) } },
        onRelease = { it.destroy() })
}
