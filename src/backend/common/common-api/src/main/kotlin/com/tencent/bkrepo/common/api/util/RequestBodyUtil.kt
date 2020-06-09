package com.tencent.bkrepo.common.api.util

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.internal.Util
import okio.BufferedSink
import okio.Okio
import okio.Source
import java.io.IOException
import java.io.InputStream

object RequestBodyUtil {
    fun create(mediaType: MediaType, inputStream: InputStream): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType {
                return mediaType
            }

            override fun contentLength(): Long {
                return try {
                    inputStream.available().toLong()
                } catch (e: IOException) {
                    return 0L
                }
            }

            override fun writeTo(sink: BufferedSink) {
                var source: Source? = null
                try {
                    source = Okio.source(inputStream)
                    sink.writeAll(source)
                } finally {
                    Util.closeQuietly(source)
                }
            }
        }
    }
}