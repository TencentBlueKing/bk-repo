/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.maven.util

import com.google.common.io.ByteStreams
import com.google.common.io.CharStreams
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.maven.enum.HashType
import org.apache.commons.lang3.StringUtils
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

    /**
     * 提取出对应的artifactId和groupId
     */
    fun extractGroupIdAndArtifactId(packageKey: String): Pair<String, String> {
        val params = PackageKeys.resolveGav(packageKey)
        val artifactId = params.split(":").last()
        val groupId = params.split(":").first()
        return Pair(artifactId, groupId)
    }

    /**
     * 获取对应package存储的节点路径
     */
    fun extractPath(packageKey: String): String {
        val (artifactId, groupId) = extractGroupIdAndArtifactId(packageKey)
        return StringUtils.join(groupId.split("."), "/") + "/$artifactId"
    }


    /**
     * 判断请求是否为checksum请求，并返回类型
     */
    fun checksumType(artifactFullPath: String): HashType? {
        var type: HashType? = null
        for (hashType in HashType.values()) {
            val suffix = ".${hashType.ext}"
            if (artifactFullPath.endsWith(suffix)) {
                type = hashType
                break
            }
        }
        return type
    }
}
