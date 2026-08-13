package com.example

import com.example.data.network.HuamiApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HuamiApiServiceTest {

    @Test
    fun parseXiaomiCode_extractsCodeCorrectly() {
        val service = HuamiApiService(org.robolectric.RuntimeEnvironment.getApplication())
        val sampleUrl = "https://hm.xiaomi.com/watch.do?code=ABC123XYZ&state=1"
        val code = service.parseXiaomiCode(sampleUrl)
        assertEquals("ABC123XYZ", code)
    }

    @Test
    fun parseXiaomiCode_returnsNullOnInvalidUrl() {
        val service = HuamiApiService(org.robolectric.RuntimeEnvironment.getApplication())
        val sampleUrl = "https://hm.xiaomi.com/watch.do?error=access_denied"
        val code = service.parseXiaomiCode(sampleUrl)
        assertNull(code)
    }

    @Test
    fun deviceId_isGeneratedWithCorrectFormat() {
        val service = HuamiApiService(org.robolectric.RuntimeEnvironment.getApplication())
        assertNotNull(service.deviceId)
        assert(service.deviceId.startsWith("02:00:00:"))
    }
}
