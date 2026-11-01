# FormFlow for Android

[![CI](https://github.com/formizeflow/formflow-android/actions/workflows/ci.yml/badge.svg)](https://github.com/formizeflow/formflow-android/actions/workflows/ci.yml)

Kotlin library `io.github.formizeflow:formflow-android` (minSdk 23). A `WebView` with a `@JavascriptInterface` bridge named `FormFlowAndroid`. No credentials in the app.

```kotlin
// Each GitHub release attaches a signed-provenance AAR. Or build from the tag with JitPack:
// settings.gradle.kts: dependencyResolutionManagement { repositories { maven("https://jitpack.io") } }
dependencies { implementation("com.github.formizeflow:formflow-android:v0.1.0") }
// Or locally: ./gradlew :formflow:publishToMavenLocal, then implementation("io.github.formizeflow:formflow-android:0.1.0")
```

## Onboarding on first launch (Compose)

```kotlin
@Composable
fun Onboarding(userId: String, done: () -> Unit) {
    FormFlowForm(
        config = FormFlowConfig(
            baseUrl = "https://forms.example.com", slug = "app-onboarding", uid = userId,
            hidden = mapOf("app_version" to BuildConfig.VERSION_NAME), showClose = true,
        ),
        modifier = Modifier.fillMaxSize(),
        onComplete = { prefs.edit().putBoolean("onboarded", true).apply(); done() },
        onClose = done,
        onTimeout = done,           // never block the app on a slow network
    )
}
```

## Views

```kotlin
val v = FormFlowView(context)
v.listener = object : FormFlowListener { override fun onComplete(e: FormFlowEvent.Complete) { ... } }
v.load(config)          // call early to preload; v.restart() starts over
```

Events: `Ready(formId, versionId, experimentId, variantId, locale)`, `View`, `Start`, `Step(index, fieldId, total)`, `Answer(fieldId, type, value)`, `Complete(submissionId, outcome, endingId, durationMs, answers)`, `Request`, `Progress`, `Close`, `Error`. Answer values exist only with `includeAnswers = true` and never for sensitive fields. Navigation is restricted to the base host; other links open in Custom Tabs. The user agent ends with `FormFlowAndroid/<version>`.

## Onboarding experiment: decide, preload, fall back

```kotlin
val client = FormFlowClient(context, "https://forms.example.com")
val req = DecideRequest("first-launch", uid = user.id, uidSignature = user.formflowSig, attributes = mapOf("plan" to user.plan),
    posthogSessionId = PostHog.getSessionId()?.toString(), posthogDistinctId = PostHog.distinctId())

lifecycleScope.launch { client.preload(req) }          // app start: warm the cache

// onboarding step
val base = FormFlowConfig("https://forms.example.com", "", uid = user.id, uidSignature = user.formflowSig,
    attributes = mapOf("plan" to user.plan), appVersion = BuildConfig.VERSION_NAME, includeAnswers = true,
    posthogSessionId = req.posthogSessionId, posthogDistinctId = req.posthogDistinctId,
    appearance = Appearance(ColorScheme.SYSTEM, accent = "#4f46e5"))
val config = runCatching { client.decide(req) }.getOrNull()?.config(base)   // null = holdout, done, or offline with no cache
if (config == null) finishOnboarding() else FormFlowForm(config,
    onStep = { if (it.fieldId == "notifications_q") { /* react mid-flow */ } },
    onRequest = { r -> if (r.action == "notifications") askNotificationPermission() else "unsupported" },
    onComplete = { save(it.answers); finishOnboarding() }, onTimeout = ::finishOnboarding, onClose = ::finishOnboarding)
```

`uidSignature` is an HMAC of `uid` computed on your server. In the form, a link like `[Enable notifications](https://example.com/#ff-action=notifications)` triggers `onRequest`; the return value is sent to the form. Run `./gradlew :formflow:testDebugUnitTest` for the parser tests.
