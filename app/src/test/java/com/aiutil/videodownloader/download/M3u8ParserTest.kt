package com.aiutil.videodownloader.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3u8ParserTest {

    @Test
    fun `parseMaster matches audio rendition group and sorts by bandwidth descending`() {
        val baseUrl = "https://video.example.com/amplify_video/1890001234567/pl/master.m3u8"
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-high",NAME="Korean, Main",DEFAULT=NO,URI="/amplify_video/1890001234567/aud/mp4a/128000/alt.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-high",NAME="Original",DEFAULT=YES,URI="/amplify_video/1890001234567/aud/mp4a/128000/main.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=832000,RESOLUTION=480x854,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="audio-high"
            /amplify_video/1890001234567/vid/avc1/480x854/low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2176000,RESOLUTION=720x1280,CODECS="avc1.640020,mp4a.40.2",AUDIO="audio-high"
            /amplify_video/1890001234567/vid/avc1/720x1280/high.m3u8
        """.trimIndent()

        assertTrue(M3u8Parser.isMaster(playlist))

        val variants = M3u8Parser.parseMaster(playlist, baseUrl)
        assertEquals(2, variants.size)

        val highest = variants[0]
        assertEquals(2176000L, highest.bandwidth)
        assertEquals(720, highest.width)
        assertEquals(1280, highest.height)
        assertEquals("1280p", highest.label)
        assertEquals(
            "https://video.example.com/amplify_video/1890001234567/vid/avc1/720x1280/high.m3u8",
            highest.url,
        )
        assertEquals(
            "https://video.example.com/amplify_video/1890001234567/aud/mp4a/128000/main.m3u8",
            highest.audioUrl,
        )

        val lowest = variants[1]
        assertEquals(832000L, lowest.bandwidth)
        assertEquals(
            "https://video.example.com/amplify_video/1890001234567/aud/mp4a/128000/main.m3u8",
            lowest.audioUrl,
        )
    }

    @Test
    fun `parseMedia extracts segments, AES-128 key, fMP4 init map, and VOD flag`() {
        val baseUrl = "https://cdn.example.com/streams/12345/media.m3u8"
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k.key",IV=0x0000000000000000000000000000000A
            #EXTINF:4.5,
            seg_10.m4s
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:3.5,
            seg_11.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        assertFalse(M3u8Parser.isMaster(playlist))

        val parsed = M3u8Parser.parseMedia(playlist, baseUrl)
        assertFalse(parsed.isLive)
        assertEquals("https://cdn.example.com/streams/12345/init.mp4", parsed.initSegmentUrl)
        assertEquals(8.0, parsed.totalDurationSec, 0.001)
        assertEquals(2, parsed.segments.size)

        val first = parsed.segments[0]
        assertEquals("https://cdn.example.com/streams/12345/seg_10.m4s", first.url)
        assertEquals(10L, first.sequence)
        assertEquals(4.5, first.durationSec, 0.001)
        assertEquals("AES-128", first.key?.method)
        assertEquals("https://keys.example.com/k.key", first.key?.keyUrl)
        assertEquals("0x0000000000000000000000000000000A", first.key?.ivHex)

        val second = parsed.segments[1]
        assertEquals("https://cdn.example.com/streams/12345/seg_11.m4s", second.url)
        assertEquals(11L, second.sequence)
        assertNull(second.key)
    }

    @Test
    fun `parseMedia marks playlist without ENDLIST as live`() {
        val baseUrl = "https://live.example.com/stream.m3u8"
        val playlist = """
            #EXTM3U
            #EXTINF:2.0,
            chunk1.ts
            #EXTINF:2.0,
            chunk2.ts
        """.trimIndent()

        val parsed = M3u8Parser.parseMedia(playlist, baseUrl)
        assertTrue(parsed.isLive)
        assertNull(parsed.initSegmentUrl)
        assertEquals(2, parsed.segments.size)
    }
}
