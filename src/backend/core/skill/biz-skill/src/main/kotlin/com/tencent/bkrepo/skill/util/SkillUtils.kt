/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.skill.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.skill.constant.FRONTMATTER_DESCRIPTION
import com.tencent.bkrepo.skill.constant.FRONTMATTER_NAME
import com.tencent.bkrepo.skill.constant.MAX_EXTRACT_FILE_SIZE
import com.tencent.bkrepo.skill.constant.SKILL_MD
import com.tencent.bkrepo.skill.constant.SLUG_MAX_LENGTH
import com.tencent.bkrepo.skill.constant.SLUG_PATTERN
import com.tencent.bkrepo.skill.pojo.metadata.SkillMetadata
import com.tencent.bkrepo.skill.pojo.request.FileInfo
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.Collator
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Skill工具类
 */
object SkillUtils {

    private val SLUG_REGEX = Regex(SLUG_PATTERN)

    /**
     * 校验slug格式是否合法
     */
    fun isValidSlug(slug: String): Boolean {
        return slug.length <= SLUG_MAX_LENGTH && SLUG_REGEX.matches(slug)
    }

    /**
     * 从SKILL.md内容解析YAML frontmatter
     * frontmatter位于文件开头两个---之间
     *
     * 示例：
     * ```yaml
     * ---
     * name: my-skill
     * description: xxx
     * ---
     * ```
     */
    @Suppress("ReturnCount")
    fun parseFrontmatter(content: String): Map<String, Any?> {
        val lines = content.lines()
        if (lines.isEmpty() || lines[0].trim() != "---") return emptyMap()

        val endIndex = lines.withIndex().filter { it.value.trim() == "---" }.getOrNull(1)?.index ?: return emptyMap()

        val yamlContent = lines.subList(1, endIndex + 1).joinToString("\n")
        if (yamlContent.isBlank()) return emptyMap()

        return try {
            yamlContent.readYamlString<Map<String, Any?>>()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun extractMetadata(frontmatter: Map<String, Any?>): SkillMetadata {
        return SkillMetadata(
            name = frontmatter[FRONTMATTER_NAME] as? String,
            description = frontmatter[FRONTMATTER_DESCRIPTION] as? String,
        )
    }

    @Suppress("LoopWithTooManyJumpStatements")
    fun extractFileFromZip(inputStream: InputStream, filePath: String): ByteArray? {
        ZipInputStream(inputStream).use { inputStream ->
            while (true) {
                val entry = inputStream.nextEntry ?: break
                if (entry.isDirectory || entry.name.removePrefix("/") != filePath) continue
                if (entry.size > MAX_EXTRACT_FILE_SIZE)
                    throw ErrorCodeException(
                        ArtifactMessageCode.ARTIFACT_SIZE_TOO_LARGE,
                        HumanReadable.size(MAX_EXTRACT_FILE_SIZE)
                    )
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(chunk).also { bytesRead = it } != -1) {
                    buffer.write(chunk, 0, bytesRead)
                }
                return buffer.toByteArray()
            }
        }
        return null
    }

    /**
     * 从zip包中读取SKILL.md内容
     */
    fun readSkillMdFromZip(inputStream: InputStream) =
        extractFileFromZip(inputStream, SKILL_MD)?.toString(Charsets.UTF_8)

    /**
     * 计算bundle fingerprint，算法与ClawHub客户端buildSkillFingerprint方法一致
     * 1. 按文件路径排列，使用Collator(Locale.US, TERTIARY)匹配JS的localeCompare排序
     * 2. 拼接为 "path:sha256" 格式，以 "\n" 连接
     * 3. 对拼接结果整体计算SHA-256
     *
     * https://github.com/openclaw/clawhub/blob/main/packages/clawhub/src/skills.ts
     */
    fun buildSkillFingerprint(fileList: List<FileInfo>): String {
        val collator = Collator.getInstance(Locale.US).apply { strength = Collator.TERTIARY }
        val payload = fileList
            .filter { it.path.isNotBlank() && it.sha256.isNotEmpty() }
            .sortedWith { a, b -> collator.compare(a.path, b.path) }
            .joinToString("\n") { "${it.path}:${it.sha256}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
