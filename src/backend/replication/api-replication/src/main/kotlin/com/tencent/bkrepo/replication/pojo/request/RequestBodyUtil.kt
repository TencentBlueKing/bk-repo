package com.tencent.bkrepo.replication.pojo.request

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.internal.Util
import okio.BufferedSink
import okio.Okio
import okio.Source
import java.io.InputStream

object RequestBodyUtil {
    fun create(mediaType: MediaType, inputStream: InputStream, length: Long): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType {
                return mediaType
            }

            override fun contentLength(): Long {
                return length
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
