package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.common.api.util.executeAndMeasureNanoTime
import com.tencent.bkrepo.common.artifact.constant.CONTENT_DISPOSITION_TEMPLATE
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.util.PathUtils
import org.springframework.boot.web.server.MimeMappings
import org.springframework.http.HttpHeaders
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object HelmZipResponseWriter {

    private const val NO_CACHE = "no-cache"
    private const val BUFFER_SIZE = 8 * 1024
    private const val NAME = "chart.zip"

    fun write(artifactResourceList: List<ArtifactResource>) {
        val response = HttpContextHolder.getResponse()

        response.bufferSize = BUFFER_SIZE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = MimeMappings.DEFAULT.get(PathUtils.getExtension(NAME).orEmpty())
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, CONTENT_DISPOSITION_TEMPLATE.format(NAME, NAME))
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_CACHE)

        val zos = ZipOutputStream(BufferedOutputStream(response.outputStream))
        try {
            artifactResourceList.forEach {
                zos.putNextEntry(ZipEntry(it.artifact))
                it.inputStream.use {
                    executeAndMeasureNanoTime {
                        it.copyTo(zos, response.bufferSize)
                    }
                    zos.closeEntry()
                    zos.flush()
                }
            }
            response.flushBuffer()
        } catch (exception: IOException) {
            val message = exception.message.orEmpty()
            when {
                message.contains("Connection reset by peer") -> {
                    LoggerHolder.logBusinessException(exception, "Stream response failed[Connection reset by peer]")
                }
                message.contains("Broken pipe") -> {
                    LoggerHolder.logBusinessException(exception, "Stream response failed[Broken pipe]")
                }
                else -> throw exception
            }
        } finally {
            zos.close()
        }
    }
}
