package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.artifact.constant.BKREPO_META
import com.tencent.bkrepo.common.artifact.constant.BKREPO_META_PREFIX
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Base64

@DisplayName("制品元数据请求头解析")
class ArtifactMetadataHeaderResolverTest {

    @Test
    fun `should resolve prefixed metadata headers case insensitively`() {
        val metadata = ArtifactMetadataHeaderResolver.resolveMetadata(
            headerNames = listOf("X-BKREPO-META-ProjectId", "x-bkrepo-meta-buildId"),
            headerValue = { name ->
                when (name.lowercase()) {
                    "x-bkrepo-meta-projectid" -> "demo"
                    "x-bkrepo-meta-buildid" -> "100"
                    else -> null
                }
            },
        )

        assertEquals("demo", metadata["projectid"])
        assertEquals("100", metadata["buildid"])
    }

    @Test
    fun `should merge base64 metadata header`() {
        val encoded = Base64.getEncoder().encodeToString("foo=bar&key=value".toByteArray())
        val metadata = ArtifactMetadataHeaderResolver.resolveMetadata(
            headerNames = listOf(BKREPO_META, "${BKREPO_META_PREFIX}fromHeader"),
            headerValue = { name ->
                when (name) {
                    BKREPO_META -> encoded
                    "${BKREPO_META_PREFIX}fromHeader" -> "1"
                    else -> null
                }
            },
        )

        assertEquals("bar", metadata["foo"])
        assertEquals("value", metadata["key"])
        assertEquals("1", metadata["fromheader"])
    }
}
