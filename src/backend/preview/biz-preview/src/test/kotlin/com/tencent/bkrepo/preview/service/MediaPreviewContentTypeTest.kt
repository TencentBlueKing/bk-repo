package com.tencent.bkrepo.preview.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("媒体预览 Content-Type")
class MediaPreviewContentTypeTest {

    @Test
    fun `maps whitelist suffixes to browser playable mime types`() {
        assertEquals("video/mp4", MediaPreviewContentType.fromSuffix("mp4"))
        assertEquals("video/webm", MediaPreviewContentType.fromSuffix("webm"))
        assertEquals("audio/mpeg", MediaPreviewContentType.fromSuffix("mp3"))
        assertEquals("audio/wav", MediaPreviewContentType.fromSuffix("wav"))
        assertEquals("audio/ogg", MediaPreviewContentType.fromSuffix("ogg"))
        assertEquals("audio/ogg", MediaPreviewContentType.fromSuffix("oga"))
        assertEquals("audio/mp4", MediaPreviewContentType.fromSuffix("m4a"))
    }

    @Test
    fun `suffix matching is case insensitive`() {
        assertEquals("video/mp4", MediaPreviewContentType.fromSuffix("MP4"))
    }

    @Test
    fun `unknown suffix has no media preview content type`() {
        assertNull(MediaPreviewContentType.fromSuffix("flv"))
        assertNull(MediaPreviewContentType.fromSuffix("mkv"))
    }
}
