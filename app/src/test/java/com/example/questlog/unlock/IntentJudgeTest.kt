package com.example.questlog.unlock

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class IntentJudgeTest {
    @Test fun `valid proxy answer parses`() = assertEquals(
        Verdict.Judged(0.91, "message"),
        parseVerdict("""{"purposeful":0.91,"category":"message"}"""),
    )

    @Test fun `junk answers are unavailable, never a grant`() {
        for (body in listOf(
            """{"purposeful":"high","category":"message"}""",
            """{"purposeful":0.9}""",
            """{"purposeful":0.9,"category":5}""",
            """{"purposeful":1.4,"category":"message"}""",
            "<html>502 Bad Gateway</html>",
            "",
        )) assertEquals(Verdict.Unavailable, parseVerdict(body), body)
    }

    @Test fun `placeholder proxy url never touches the network`() = runTest {
        val judge = IntentJudge("REPLACE_WITH_UNLOCK_PROXY_URL") { error("install id must not be read") }
        assertEquals(Verdict.Unavailable, judge.judge("Instagram", "reply to mum"))
    }

    @Test fun `long app labels are truncated to what the proxy accepts`() {
        val body = kotlinx.serialization.json.Json.parseToJsonElement(judgeRequestBody("A".repeat(90), "reply to mum"))
        val app = (body as kotlinx.serialization.json.JsonObject)["app"].toString().trim('"')
        assertEquals(60, app.length)
    }
}
