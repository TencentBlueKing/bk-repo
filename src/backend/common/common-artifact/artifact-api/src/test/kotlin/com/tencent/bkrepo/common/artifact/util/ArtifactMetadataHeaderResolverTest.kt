package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.artifact.constant.BKREPO_META
import com.tencent.bkrepo.common.artifact.constant.BKREPO_META_PREFIX
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
            assertEquals("2025-01-01 00:00:00+08:00", ArtifactMetadataHeaderResolver.decodeHeaderValue("2025-01-01%2000%3A00%3A00+08%3A00"))
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
    }
}
