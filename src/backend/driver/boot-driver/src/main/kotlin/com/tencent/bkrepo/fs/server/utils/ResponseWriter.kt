package com.tencent.bkrepo.fs.server.utils

import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.fs.server.io.RegionInputStreamResource
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ZeroCopyHttpOutputMessage
import org.springframework.http.server.reactive.ServerHttpResponse

object ResponseWriter {
    suspend fun ServerHttpResponse.writeStream(artifactInputStream: ArtifactInputStream, range: Range) {
        if (range.isPartialContent()) {
            statusCode = HttpStatus.PARTIAL_CONTENT
        }
        headers.contentLength = artifactInputStream.range.length
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes")
        headers.add("Content-Range", "bytes ${range.start}-${range.end}/${range.total}")
        artifactInputStream.use {
            if (artifactInputStream is FileArtifactInputStream) {
                (this as ZeroCopyHttpOutputMessage).writeWith(
                    artifactInputStream.file,
                    artifactInputStream.range.start,
                    artifactInputStream.range.length
                ).awaitSingleOrNull()
            } else {
                val source = RegionInputStreamResource(artifactInputStream, range.total!!)
                val body =
                    DataBufferUtils.read(source, DefaultDataBufferFactory.sharedInstance, DEFAULT_BUFFER_SIZE)
                writeWith(body).awaitSingleOrNull()
            }
        }
    }
}
