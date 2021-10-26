package com.tencent.bkrepo.maven.util

import com.google.common.io.ByteStreams
import com.google.common.io.CharStreams
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object MavenUtil {
    private const val MAX_DIGEST_CHARS_NEEDED = 128

    /**
     * 从流中导出摘要
     * */
    fun extractDigest(inputStream: InputStream): String {
        inputStream.use {
            val reader = InputStreamReader(
                ByteStreams
                    .limit(inputStream, MAX_DIGEST_CHARS_NEEDED.toLong()),
                StandardCharsets.UTF_8
            )
            return CharStreams.toString(reader)
        }
    }
}
