package com.formflow

import org.junit.Assert.*
import org.junit.Test

class FormFlowTest {
    @Test fun urlEncodesEverything() {
        val c = FormFlowConfig("https://f.example.com/", "on boarding", uid = "u 1&x", variant = "v2", locale = "de",
            hidden = mapOf("plan" to "pro/1"), prefill = mapOf("email" to "a+b@c.co"), includeAnswers = true, autoAttributes = false)
        assertEquals("https://f.example.com/embed/on%20boarding?native=1&uid=u%201%26x&variant=v2&lang=de&answers=1&hidden.plan=pro%2F1&prefill.email=a%2Bb%40c.co", c.url())
    }

    @Test fun identityAppearanceAndAttributes() {
        val c = FormFlowConfig("https://f.co", "x", uid = "u", uidSignature = "ab/c=", posthogSessionId = "s1", posthogDistinctId = "d1", resume = false,
            appearance = Appearance(ColorScheme.DARK, "#112233", "Inter"), attributes = mapOf("plan" to "pro"), autoAttributes = false)
        assertEquals("https://f.co/embed/x?native=1&uid=u&uid_sig=ab%2Fc%3D&resume=0&ph_session=s1&ph_distinct=d1&scheme=dark&accent=%23112233&font=Inter&attrs.plan=pro", c.url())
        assertTrue(c.url(restart = true).contains("resume=0"))
    }

    @Test fun answersOffByDefault() {
        assertEquals("https://f.co/embed/x?native=1", FormFlowConfig("https://f.co", "x", autoAttributes = false).url())
    }

    @Test fun navigationAllowList() {
        val c = FormFlowConfig("https://f.example.com", "x")
        assertTrue(c.isAllowed("https://f.example.com/embed/x"))
        assertFalse(c.isAllowed("https://f.example.com.evil.com/"))
        assertFalse(c.isAllowed("https://evil.com/"))
    }

    @Test fun parsesReadyStepAnswer() {
        val r = FormFlowEvent.parse("""{"source":"formflow","type":"ready","formId":"f1","data":{"formId":"f1","versionId":"v9","experimentId":"e1","variantId":"b","locale":"en"}}""")
        assertEquals(FormFlowEvent.Ready("f1", "v9", "e1", "b", "en"), r)
        assertEquals(FormFlowEvent.Step(2, "a", 5), FormFlowEvent.parse("""{"source":"formflow","type":"step","data":{"index":2,"fieldId":"a","total":5}}"""))
        assertEquals(FormFlowEvent.Answer("a", "short_text", null), FormFlowEvent.parse("""{"source":"formflow","type":"answer","data":{"fieldId":"a","type":"short_text"}}"""))
        assertEquals(FormFlowEvent.Answer("a", "short_text", "hi"), FormFlowEvent.parse("""{"source":"formflow","type":"answer","data":{"fieldId":"a","type":"short_text","value":"hi"}}"""))
    }

    @Test fun parsesCompleteWithAnswers() {
        val e = FormFlowEvent.parse("""{"source":"formflow","type":"complete","data":{"submissionId":"s1","outcome":{"passed":true,"endingId":"end"},"endingId":"end","durationMs":1200,"answers":{"a":"x","m":["p","q"]}}}""") as FormFlowEvent.Complete
        assertEquals("s1", e.submissionId)
        assertEquals(Outcome(true, null, "end"), e.outcome)
        assertEquals(1200L, e.durationMs)
        assertEquals("x", e.answers!!["a"])
        assertEquals(listOf("p", "q"), e.answers!!["m"])
    }

    @Test fun parsesRequest() {
        val e = FormFlowEvent.parse("""{"source":"formflow","type":"request","data":{"requestId":"r1","action":"notifications","params":{"topic":"news"}}}""")
        assertEquals(FormFlowEvent.Request(FormFlowRequest("r1", "notifications", mapOf("topic" to "news"))), e)
    }

    @Test fun ignoresForeignAndFolded() {
        assertNull(FormFlowEvent.parse("garbage"))
        assertNull(FormFlowEvent.parse("""{"source":"other","type":"close"}"""))
        assertNull(FormFlowEvent.parse("""{"source":"formflow","type":"submit","data":{}}"""))
        assertEquals(FormFlowEvent.Close, FormFlowEvent.parse("""{"source":"formflow","type":"close","data":{}}"""))
    }

    @Test fun decisions() {
        val d = FormFlowDecision.parse("""{"data":{"show":true,"variantId":"b","slug":"onb","reason":"variant"}}""")!!
        val base = FormFlowConfig("https://f.co", "")
        assertEquals("b", d.config(base)!!.variant)
        assertEquals("onb", d.config(base)!!.slug)
        assertNull(FormFlowDecision.parse("""{"show":false,"reason":"holdout"}""")!!.config(base))
        assertNull(FormFlowDecision.parse("nope"))
    }
}
