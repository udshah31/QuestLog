package com.example.questlog.unlock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchAppTest {
    private fun context(launch: Intent?, throws: Throwable? = null): Context {
        val pm = mock<PackageManager> { on { getLaunchIntentForPackage("com.insta") } doReturn launch }
        return mock<Context> {
            on { packageManager } doReturn pm
        }.also { ctx -> if (throws != null) whenever(ctx.startActivity(any())).doThrow(throws) }
    }

    @Test fun `launches when the app has a launch intent`() = assertTrue(launchApp(context(mock()), "com.insta"))

    @Test fun `no launch intent is a soft failure`() = assertFalse(launchApp(context(null), "com.insta"))

    @Test fun `a disabled or restricted app does not crash`() {
        assertFalse(launchApp(context(mock(), ActivityNotFoundException()), "com.insta"))
        assertFalse(launchApp(context(mock(), SecurityException()), "com.insta"))
    }
}
