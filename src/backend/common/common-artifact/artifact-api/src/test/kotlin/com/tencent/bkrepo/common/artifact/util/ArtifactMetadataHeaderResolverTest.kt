package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.artifact.constant.BKREPO_META
import com.tencent.bkrepo.common.artifact.constant.BKREPO_META_PREFIX
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.Locale
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

    // ===== decodeHeaderValue 行为测试 —— 与 UriUtils.decode 对齐 =====

    @Nested
    @DisplayName("decodeHeaderValue 行为")
    inner class DecodeHeaderValueBehavior {

        @Test
        fun `should keep plus sign as literal`() {
            // UriUtils.decode 保留 + 为字面量，URLDecoder.decode 默认会把 + 转空格，需要兼容
            assertEquals("a+b", ArtifactMetadataHeaderResolver.decodeHeaderValue("a+b"))
        }

        @Test
        fun `should decode percent encoded space to space`() {
            assertEquals("a b", ArtifactMetadataHeaderResolver.decodeHeaderValue("a%20b"))
        }

        @Test
        fun `should decode percent encoded plus to plus`() {
            assertEquals("a+b", ArtifactMetadataHeaderResolver.decodeHeaderValue("a%2Bb"))
        }

        @Test
        fun `should handle mixed plus and percent encodings correctly`() {
            // + 是字面量加号，%20 是空格
            val input = "2025-01-01%2000%3A00%3A00+08%3A00"
            assertEquals("2025-01-01 00:00:00+08:00", ArtifactMetadataHeaderResolver.decodeHeaderValue(input))
        }

        @Test
        fun `should decode chinese characters`() {
            assertEquals("中文", ArtifactMetadataHeaderResolver.decodeHeaderValue("%E4%B8%AD%E6%96%87"))
        }

        @Test
        fun `should return null for null input`() {
            assertNull(ArtifactMetadataHeaderResolver.decodeHeaderValue(null))
        }

        @Test
        fun `should return original value on invalid encoding`() {
            // 非法编码序列应回退到原值，不抛异常
            assertEquals("%ZZ", ArtifactMetadataHeaderResolver.decodeHeaderValue("%ZZ"))
        }
    }

    // ===== resolveMetadata 端到端测试 —— 覆盖 + 和百分号编码 =====

    @Nested
    @DisplayName("resolveMetadata 含特殊字符")
    inner class ResolveMetadataWithSpecialChars {

        @Test
        fun `should preserve plus sign in prefixed header value`() {
            val metadata = ArtifactMetadataHeaderResolver.resolveMetadata(
                headerNames = listOf("X-BKREPO-META-timezone"),
                headerValue = { "UTC+08:00" },
            )
            assertEquals("UTC+08:00", metadata["timezone"])
        }

        @Test
        fun `should decode percent encoding in prefixed header value`() {
            val metadata = ArtifactMetadataHeaderResolver.resolveMetadata(
                headerNames = listOf("X-BKREPO-META-path"),
                headerValue = { "dir%2Fsub%20file" },
            )
            assertEquals("dir/sub file", metadata["path"])
        }

        @Test
        fun `should preserve plus in base64 metadata values`() {
            // base64("key=UTC+08:00") → key 的 value 包含 +
            val encoded = Base64.getEncoder().encodeToString("key=UTC+08:00".toByteArray())
            val metadata = ArtifactMetadataHeaderResolver.resolveMetadata(
                headerNames = listOf(BKREPO_META),
                headerValue = { encoded },
            )
            assertEquals("UTC+08:00", metadata["key"])
        }

        @Test
        fun `should decode percent encoding in base64 metadata values`() {
            // key=中%2F文, 百分号编码的 /
            val encoded = Base64.getEncoder().encodeToString("key=%E4%B8%AD%2F%E6%96%87".toByteArray())
            val metadata = ArtifactMetadataHeaderResolver.resolveMetadata(
                headerNames = listOf(BKREPO_META),
                headerValue = { encoded },
            )
            assertEquals("中/文", metadata["key"])
        }

        @Test
        fun `should handle combined base64 and prefixed headers with special chars`() {
            val encoded = Base64.getEncoder().encodeToString("b64key=val+1".toByteArray())
            val metadata = ArtifactMetadataHeaderResolver.resolveMetadata(
                headerNames = listOf(BKREPO_META, "X-BKREPO-META-prefixKey"),
                headerValue = { name ->
                    when (name) {
                        BKREPO_META -> encoded
                        "X-BKREPO-META-prefixKey" -> "val%20with%20space"
                        else -> null
                    }
                },
            )
            assertEquals("val+1", metadata["b64key"])
            assertEquals("val with space", metadata["prefixkey"])
        }

        @Test
        fun `should keep blank prefixed header value`() {
            val metadata = ArtifactMetadataHeaderResolver.resolveMetadata(
                headerNames = listOf("X-BKREPO-META-empty"),
                headerValue = { "" },
            )
            assertEquals("", metadata["empty"])
        }
    }

    @Nested
    @DisplayName("与 master GenericLocalRepository 行为对齐")
    inner class MasterGenericLocalRepositoryParity {

        private fun masterResolveMetadata(
            headerNames: Iterable<String>,
            headerValue: (String) -> String?,
            extraMetadata: Map<String, String>? = null,
        ): Map<String, String> {
            val metadata = mutableMapOf<String, String>()
            for (headerName in headerNames) {
                if (headerName.startsWith(BKREPO_META_PREFIX, ignoreCase = true)) {
                    val key = headerName.substring(BKREPO_META_PREFIX.length)
                        .trim().lowercase(Locale.getDefault())
                    if (key.isNotBlank()) {
                        metadata[key] = decodeLikeHeaderUtils(headerValue(headerName))!!
                    }
                }
            }
            headerValue(BKREPO_META)?.let { metadata.putAll(masterDecodeMetadata(it)) }
            extraMetadata?.let { metadata.putAll(it) }
            return metadata
        }

        private fun decodeLikeHeaderUtils(headerValue: String?): String? {
            return headerValue?.let {
                try {
                    org.springframework.web.util.UriUtils.decode(it, Charsets.UTF_8)
                } catch (_: IllegalArgumentException) {
                    it
                }
            }
        }

        private fun masterDecodeMetadata(header: String): Map<String, String> {
            val metadata = mutableMapOf<String, String>()
            try {
                val metadataUrl = String(Base64.getDecoder().decode(header))
                metadataUrl.split(CharPool.AND).forEach { part ->
                    val pair = part.trim().split(CharPool.EQUAL, limit = 2)
                    if (pair.size > 1 && pair[0].isNotBlank() && pair[1].isNotBlank()) {
                        val key = org.springframework.web.util.UriUtils.decode(pair[0], Charsets.UTF_8)
                        val value = org.springframework.web.util.UriUtils.decode(pair[1], Charsets.UTF_8)
                        metadata[key] = value
                    }
                }
            } catch (_: IllegalArgumentException) {
                // ignore, same as master
            }
            return metadata
        }

        @Test
        fun `should match master resolveMetadata for representative inputs`() {
            val headerNames = listOf(
                "X-BKREPO-META-ProjectId",
                "x-bkrepo-meta-buildId",
                "X-BKREPO-META-timezone",
                "X-BKREPO-META-path",
                "X-BKREPO-META-empty",
                BKREPO_META,
            )
            val encoded = Base64.getEncoder().encodeToString("foo=bar&key=UTC+08%3A00".toByteArray())
            val headerValue: (String) -> String? = { name ->
                when (name) {
                    "X-BKREPO-META-ProjectId" -> "demo"
                    "x-bkrepo-meta-buildId" -> "100"
                    "X-BKREPO-META-timezone" -> "UTC+08:00"
                    "X-BKREPO-META-path" -> "dir%2Fsub%20file"
                    "X-BKREPO-META-empty" -> ""
                    BKREPO_META -> encoded
                    else -> null
                }
            }
            val extra = mapOf("pipeline" to "p1")

            val expected = masterResolveMetadata(headerNames, headerValue, extra)
            val actual = ArtifactMetadataHeaderResolver.resolveMetadata(headerNames, headerValue, extra)

            assertEquals(expected, actual)
        }
    }
}
