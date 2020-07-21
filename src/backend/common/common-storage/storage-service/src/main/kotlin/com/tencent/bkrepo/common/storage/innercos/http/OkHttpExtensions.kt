package com.tencent.bkrepo.common.storage.innercos.http

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.internal.Util.checkOffsetAndCount
import okio.BufferedSink
import java.io.Closeable
import java.nio.charset.Charset

fun String.toRequestBody(contentType: MediaType? = null): RequestBody {
    var charset: Charset = Charsets.UTF_8
    var finalContentType: MediaType? = contentType
    if (contentType != null) {
        val resolvedCharset = contentType.charset()
        if (resolvedCharset == null) {
            charset = Charsets.UTF_8
            finalContentType = "$contentType; charset=utf-8".toMediaTypeOrNull()
        } else {
            charset = resolvedCharset
        }
    }
    val bytes = toByteArray(charset)
    return bytes.toRequestBody(finalContentType, 0, bytes.size)
}

fun ByteArray.toRequestBody(
    contentType: MediaType? = null,
    offset: Int = 0,
    byteCount: Int = size
): RequestBody {
    checkOffsetAndCount(size.toLong(), offset.toLong(), byteCount.toLong())
    return object : RequestBody() {
        override fun contentType() = contentType

        override fun contentLength() = byteCount.toLong()

        override fun writeTo(sink: BufferedSink) {
            sink.write(this@toRequestBody, offset, byteCount)
        }
    }
}

fun String.toMediaTypeOrNull(): MediaType? {
    return try {
        MediaType.parse(this)
    } catch (_: IllegalArgumentException) {
        null
    }
}

inline fun <T : Closeable?, R> T.useOnCondition(condition: Boolean, block: (T) -> R): R {
    return if (condition) {
        use(block)
    } else {
        block(this)
    }
}
