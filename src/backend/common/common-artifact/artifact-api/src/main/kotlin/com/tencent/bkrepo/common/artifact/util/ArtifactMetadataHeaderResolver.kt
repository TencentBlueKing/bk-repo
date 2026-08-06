package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.artifact.constant.BKREPO_META
import com.tencent.bkrepo.common.artifact.constant.BKREPO_META_PREFIX
import org.slf4j.LoggerFactory
import org.springframework.web.util.UriUtils
import java.util.Base64
import java.util.Locale

object ArtifactMetadataHeaderResolver {

    fun resolveMetadata(
        headerNames: Iterable<String>,
        headerValue: (String) -> String?,
        extraMetadata: Map<String, String>? = null,
    ): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        for (headerName in headerNames) {
            if (!headerName.startsWith(BKREPO_META_PREFIX, ignoreCase = true)) {
                continue
            }
            val key = headerName.substring(BKREPO_META_PREFIX.length)
                .trim().lowercase(Locale.getDefault())
            if (key.isNotBlank()) {
                metadata[key] = decodeHeaderValue(headerValue(headerName))!!
            }
        }
        headerValue(BKREPO_META)?.let { metadata.putAll(decodeMetadata(it)) }
        extraMetadata?.let { metadata.putAll(it) }
        return metadata
    }

    fun decodeHeaderValue(headerValue: String?): String? {
        return headerValue?.let {
            try {
                UriUtils.decode(it, Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                it
            }
        }
    }

    fun decodeMetadata(header: String): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        try {
            val metadataUrl = String(Base64.getDecoder().decode(header))
            metadataUrl.split(CharPool.AND).forEach { part ->
                val pair = part.trim().split(CharPool.EQUAL, limit = 2)
                if (pair.size > 1 && pair[0].isNotBlank() && pair[1].isNotBlank()) {
                    val key = UriUtils.decode(pair[0], Charsets.UTF_8)
                    val value = UriUtils.decode(pair[1], Charsets.UTF_8)
                    metadata[key] = value
                }
            }
        } catch (_: IllegalArgumentException) {
            logger.warn("$header is not in valid Base64 scheme.")
        }
        return metadata
    }

    private val logger = LoggerFactory.getLogger(ArtifactMetadataHeaderResolver::class.java)
}
