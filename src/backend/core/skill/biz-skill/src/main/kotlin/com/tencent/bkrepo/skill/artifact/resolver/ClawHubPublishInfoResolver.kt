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

package com.tencent.bkrepo.skill.artifact.resolver

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.common.metadata.util.version.SemVersion
import com.tencent.bkrepo.skill.constant.SKILL_MD
import com.tencent.bkrepo.skill.constant.SKILL_MD_PARSING_MAX_SIZE
import com.tencent.bkrepo.skill.constant.SKILL_MD_SHOWING_MAX_SIZE
import com.tencent.bkrepo.skill.constant.SkillMessageCode
import com.tencent.bkrepo.skill.constant.TEXT_FILE_EXTENSIONS
import com.tencent.bkrepo.skill.exception.ClawHubPayloadInvalidException
import com.tencent.bkrepo.skill.exception.ClawHubPayloadMissingException
import com.tencent.bkrepo.skill.exception.ClawHubSkillMdMissingException
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubPublishInfo
import com.tencent.bkrepo.skill.pojo.request.ClawHubPublishPayload
import com.tencent.bkrepo.skill.pojo.request.FileInfo
import com.tencent.bkrepo.skill.util.SkillUtils
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@Resolver(ClawHubPublishInfo::class)
class ClawHubPublishInfoResolver : ArtifactInfoResolver {

    @Suppress("UNCHECKED_CAST")
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): ArtifactInfo {
        // 解析和验证payload
        val payload = try {
            request.getPart("payload")?.inputStream?.use { it.readJsonString<ClawHubPublishPayload>() }
        } catch (e: Exception) {
            logger.error("failed to resolve clawhub publish payload: ", e)
            throw ClawHubPayloadInvalidException()
        } ?: throw ClawHubPayloadMissingException()
        checkPayload(payload)

        // 解析和验证SKILL.md，要求SKILL.md文件存在且在上传目录的根路径下
        val artifactFiles = (request.getAttribute("artifact.files") as List<MultipartArtifactFile>)
        val fileList = buildFileList(artifactFiles)
        val fingerprint = computeFingerprint(fileList)
        val skillMdFile = artifactFiles.find { it.getOriginalFilename() == SKILL_MD } ?: run {
            logger.warn("SKILL.md not found in upload for skill [${payload.slug}], metadata may be incomplete")
            throw ClawHubSkillMdMissingException()
        }
        val skillMdSize = skillMdFile.getSize()
        val (skillMetadata, skillMdContent) =
            if (skillMdSize > SKILL_MD_PARSING_MAX_SIZE) {
                logger.info("SKILL.md exceeds max size [$SKILL_MD_PARSING_MAX_SIZE] for skill [${payload.slug}]")
                null to null
            } else {
                val skillMdContent = skillMdFile.getInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
                val frontmatter = SkillUtils.parseFrontmatter(skillMdContent)
                val skillMetadata = SkillUtils.extractMetadata(frontmatter)
                skillMetadata to if (skillMdSize > SKILL_MD_SHOWING_MAX_SIZE) null else skillMdContent
            }
        return ClawHubPublishInfo(projectId, repoName, payload, skillMetadata, skillMdContent, fileList, fingerprint)
    }

    private fun checkPayload(payload: ClawHubPublishPayload) {
        if (!SkillUtils.isValidSlug(payload.slug)) {
            throw ErrorCodeException(SkillMessageCode.SKILL_SLUG_INVALID, payload.slug)
        }
        try {
            SemVersion.parse(payload.version)
        } catch (_: Exception) {
            throw ErrorCodeException(SkillMessageCode.SKILL_VERSION_INVALID, payload.version)
        }
    }

    private fun buildFileList(fileList: List<MultipartArtifactFile>): List<FileInfo> {
        return fileList.mapNotNull { file ->
            val sanitizedName = sanitizePath(file.getOriginalFilename()) ?: return@mapNotNull null
            val ext = sanitizedName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty() || ext !in TEXT_FILE_EXTENSIONS) return@mapNotNull null
            FileInfo(path = sanitizedName, size = file.getSize(), sha256 = file.getFileSha256())
        }
    }

    private fun computeFingerprint(fileList: List<FileInfo>): String {
        return if (fileList.isEmpty()) {
            throw ClawHubPayloadInvalidException()
        } else {
            SkillUtils.buildSkillFingerprint(fileList)
        }
    }

    private fun sanitizePath(path: String): String? {
        val normalized = path
            .replace(Regex("^\\./+"), "")
            .replace(Regex("^/+"), "")
        if (normalized.isBlank() || normalized.endsWith("/")) return null
        if (normalized.contains("..") || normalized.contains("\\")) return null
        return normalized
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClawHubPublishInfoResolver::class.java)
    }
}
