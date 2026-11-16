package com.aiutil.videodownloader.data

import com.aiutil.videodownloader.download.HlsDownloader
import com.aiutil.videodownloader.model.DetectedVideo
import com.aiutil.videodownloader.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionStoreTest {

    @Test
    fun `hlsCandidatePriority ranks master before generic, variant video, and non-video`() {
        val master = "https://video.twimg.com/amplify_video/1890001234567/pl/master.m3u8?tag=14"
        val generic = "https://cdn.example.com/hls/1890001234567/stream.m3u8"
        val variantVideo = "https://video.twimg.com/amplify_video/1890001234567/vid/avc1/720x1280/high.m3u8"
        val audioOnly = "https://video.twimg.com/amplify_video/1890001234567/aud/mp4a/128000/audio.m3u8"
        val subtitle = "https://video.twimg.com/amplify_video/1890001234567/sub/ko.m3u8"

        assertEquals(0, HlsDownloader.hlsCandidatePriority(master))
        assertEquals(1, HlsDownloader.hlsCandidatePriority(generic))
        assertEquals(2, HlsDownloader.hlsCandidatePriority(variantVideo))
        assertEquals(3, HlsDownloader.hlsCandidatePriority(audioOnly))
        assertEquals(3, HlsDownloader.hlsCandidatePriority(subtitle))
    }

    @Test
    fun `onScreenVideos prefers master playlist over later variant video and audio tracks with same media id`() {
        val store = DetectionStore()
        val pageUrl = "https://x.com/home"
        val postUrl = "https://x.com/user/status/9876543210123"
        val mediaId = "1890001234567"

        val masterUrl = "https://video.twimg.com/amplify_video/$mediaId/pl/abcdef.m3u8?tag=14"
        val variantVideoUrl = "https://video.twimg.com/amplify_video/$mediaId/vid/avc1/720x1280/xyz.m3u8"
        val audioUrl = "https://video.twimg.com/amplify_video/$mediaId/aud/mp4a/128000/xyz.m3u8"
        val subtitleUrl = "https://video.twimg.com/amplify_video/$mediaId/sub/ko.m3u8"

        // 실제 네트워크 요청 순서: 마스터 -> 비디오 전용 -> 오디오 전용 -> 자막
        store.add(
            DetectedVideo(
                url = masterUrl,
                pageUrl = pageUrl,
                pageTitle = "X",
                kind = MediaKind.HLS,
                detectedAt = 1_000L,
            )
        )
        store.add(
            DetectedVideo(
                url = variantVideoUrl,
                pageUrl = pageUrl,
                pageTitle = "X",
                kind = MediaKind.HLS,
                detectedAt = 2_000L,
            )
        )
        store.add(
            DetectedVideo(
                url = audioUrl,
                pageUrl = pageUrl,
                pageTitle = "X",
                kind = MediaKind.HLS,
                detectedAt = 3_000L,
            )
        )
        store.add(
            DetectedVideo(
                url = subtitleUrl,
                pageUrl = pageUrl,
                pageTitle = "X",
                kind = MediaKind.HLS,
                detectedAt = 4_000L,
            )
        )

        store.setOnScreen(
            items = listOf(
                DetectionStore.ScreenItem(
                    urls = emptyList(),
                    posterUrl = "https://pbs.twimg.com/amplify_video_thumb/$mediaId/img/thumb.jpg",
                    link = postUrl,
                )
            ),
            count = 1,
            changed = true,
        )

        val onScreen = store.onScreenVideos.value
        assertEquals(4, onScreen.size)
        // 나중에 들어온 비디오/오디오/자막 트랙보다 마스터 플레이리스트가 최상단이어야 한다.
        assertEquals(masterUrl, onScreen[0].url)
        assertEquals(variantVideoUrl, onScreen[1].url)
        assertEquals(postUrl, onScreen[0].pageUrl)
        assertTrue(DetectionStore.extractMediaKeys(masterUrl).contains(mediaId))
    }
}
