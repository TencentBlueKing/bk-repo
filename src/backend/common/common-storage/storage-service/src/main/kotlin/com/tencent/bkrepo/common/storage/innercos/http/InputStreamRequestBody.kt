package com.tencent.bkrepo.common.storage.innercos.http

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Okio
import java.io.InputStream

class InputStreamRequestBody(
    private val inputStream: InputStream
) : RequestBody() {

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long {
        return inputStream.available().toLong()
    }

    override fun writeTo(sink: BufferedSink) {
        Okio.source(inputStream).use { source -> sink.writeAll(source) }
    }
}
