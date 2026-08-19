package com.tencent.bkrepo.preview.pojo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("预览文件类型映射")
class FileTypeTest {

    @ParameterizedTest
    @ValueSource(strings = ["demo.mp4", "demo.webm", "demo.mp3", "demo.wav", "demo.ogg", "demo.oga", "demo.m4a"])
    fun `whitelist media suffixes map to MEDIA`(fileName: String) {
        assertEquals(FileType.MEDIA, FileType.typeFromFileName(fileName))
    }

    @Test
    fun `flv is not a previewable media type`() {
        assertEquals(FileType.OTHER, FileType.typeFromFileName("demo.flv"))
    }

    @Test
    fun `media mapping is case insensitive`() {
        assertEquals(FileType.MEDIA, FileType.typeFromFileName("Demo.MP4"))
        assertEquals(FileType.OTHER, FileType.typeFromFileName("Demo.FLV"))
    }
}
