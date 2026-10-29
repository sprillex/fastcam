package com.sprillex.rtspviewer

import org.junit.Assert.*
import org.junit.Test

class RtspUriUtilsTest {

    @Test
    fun testIsValidRtspUri_validUris() {
        assertTrue(RtspUriUtils.isValidRtspUri("rtsp://192.168.1.100:554/live"))
        assertTrue(RtspUriUtils.isValidRtspUri("rtsp://admin:secret@10.0.0.1:554/stream1"))
        assertTrue(RtspUriUtils.isValidRtspUri("rtsps://camera.local/h264"))
    }

    @Test
    fun testIsValidRtspUri_invalidUris() {
        assertFalse(RtspUriUtils.isValidRtspUri(null))
        assertFalse(RtspUriUtils.isValidRtspUri(""))
        assertFalse(RtspUriUtils.isValidRtspUri("http://192.168.1.100/video"))
        assertFalse(RtspUriUtils.isValidRtspUri("not_a_url"))
    }

    @Test
    fun testMaskCredentials() {
        assertEquals(
            "rtsp://admin:****@192.168.1.100:554/live",
            RtspUriUtils.maskCredentials("rtsp://admin:mypassword123@192.168.1.100:554/live")
        )
        assertEquals(
            "rtsp://192.168.1.100:554/live",
            RtspUriUtils.maskCredentials("rtsp://192.168.1.100:554/live")
        )
        assertEquals(
            "rtsp://****@192.168.1.100:554/live",
            RtspUriUtils.maskCredentials("rtsp://admin@192.168.1.100:554/live")
        )
        assertEquals("[EMPTY]", RtspUriUtils.maskCredentials(null))
    }

    @Test
    fun testBuildRtspUri() {
        assertEquals(
            "rtsp://admin:pass@192.168.1.50:554/stream",
            RtspUriUtils.buildRtspUri("192.168.1.50", 554, "stream", "admin", "pass")
        )
        assertEquals(
            "rtsp://192.168.1.50:554/stream",
            RtspUriUtils.buildRtspUri("192.168.1.50", 554, "/stream")
        )
    }
}
