package com.thelightphone.lightpage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeIntegrationTest {

    @Test
    fun packageNameMatchesToolId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.thelightphone.lightpage", context.packageName)
    }
}
