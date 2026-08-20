package com.tencent.bkrepo.preview.service

/**
 * 媒体预览按后缀强制的响应 Content-Type。
 */
object MediaPreviewContentType {
    private val SUFFIX_TO_CONTENT_TYPE = mapOf(
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "m4a" to "audio/mp4"
    )

    /**
     * 将媒体后缀映射为浏览器可播放的 MIME。未知后缀返回 null。
     */
    fun fromSuffix(suffix: String): String? {
        return SUFFIX_TO_CONTENT_TYPE[suffix.lowercase()]
    }
}
