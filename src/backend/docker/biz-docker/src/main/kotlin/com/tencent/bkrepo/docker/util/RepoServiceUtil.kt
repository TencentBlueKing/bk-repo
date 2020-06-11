package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.constant.HTTP_FORWARDED_PROTO
import com.tencent.bkrepo.docker.constant.HTTP_PROTOCOL_HTTP
import com.tencent.bkrepo.docker.constant.HTTP_PROTOCOL_HTTPS
import com.tencent.bkrepo.docker.errors.DockerV2Errors
import com.tencent.bkrepo.docker.manifest.ManifestType
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import java.io.InputStream
import java.util.Objects
import java.util.regex.Pattern
import kotlin.streams.toList

class RepoServiceUtil {

    companion object {

        private val logger = LoggerFactory.getLogger(RepoServiceUtil::class.java)
        private val OLD_USER_AGENT_PATTERN = Pattern.compile("^(?:docker\\/1\\.(?:3|4|5|6|7(?!\\.[0-9]-dev))|Go ).*$")

        fun putHasStream(httpHeaders: HttpHeaders): Boolean {
            val headerValues = httpHeaders["User-Agent"]
            if (headerValues != null) {
                val headerIter = headerValues.iterator()
                while (headerIter.hasNext()) {
                    val userAgent = headerIter.next() as String
                    logger.info("User agent header: [$userAgent]")
                    if (OLD_USER_AGENT_PATTERN.matcher(userAgent).matches()) {
                        return true
                    }
                }
            }
            return false
        }

        fun consumeStreamAndReturnError(stream: InputStream): ResponseEntity<Any> {
            NullOutputStream().use {
                IOUtils.copy(stream, it)
            }
            return DockerV2Errors.unauthorizedUpload()
        }

        fun getProtocol(httpHeaders: HttpHeaders): String {
            val protocolHeaders = httpHeaders[HTTP_FORWARDED_PROTO]
            if (protocolHeaders == null || protocolHeaders.isEmpty()) {
                return HTTP_PROTOCOL_HTTP
            }
            return if (protocolHeaders.isNotEmpty()) {
                protocolHeaders.iterator().next() as String
            } else {
                logger.debug("X-Forwarded-Proto does not exist, return https.")
                HTTP_PROTOCOL_HTTPS
            }
        }

        fun getAcceptableManifestTypes(httpHeaders: HttpHeaders): List<ManifestType> {
            return httpHeaders.accept.stream().filter { Objects.nonNull(it) }.map { ManifestType.from(it) }.toList()
        }
    }
}
