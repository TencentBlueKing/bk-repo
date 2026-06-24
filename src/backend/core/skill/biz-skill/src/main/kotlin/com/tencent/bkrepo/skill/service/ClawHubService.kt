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

package com.tencent.bkrepo.skill.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactMultiFileMap
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.skill.constant.MAX_UPLOAD_FILE_COUNT
import com.tencent.bkrepo.skill.constant.MAX_UPLOAD_TOTAL_SIZE
import com.tencent.bkrepo.skill.constant.SkillMessageCode
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubPublishInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubArchiveDownloadInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubFileDownloadInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSearchInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillListInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillVersionInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillVersionListInfo
import com.tencent.bkrepo.skill.pojo.response.ClawHubPublishResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillDetailResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillListResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillVersionDetailResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillVersionListResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSearchResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSearchResultItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class ClawHubService(
    private val packageService: PackageService,
) : ArtifactService() {

    fun listSkills(artifactInfo: ClawHubSkillListInfo): ClawHubSkillListResponse {
        val context = ArtifactQueryContext()
        return repository.query(context) as ClawHubSkillListResponse
    }

    fun getSkillDetail(artifactInfo: ClawHubSkillInfo): ClawHubSkillDetailResponse {
        val context = ArtifactQueryContext()
        return repository.query(context) as ClawHubSkillDetailResponse
    }

    fun listSkillVersions(artifactInfo: ClawHubSkillVersionListInfo): ClawHubSkillVersionListResponse {
        val context = ArtifactQueryContext()
        return repository.query(context) as ClawHubSkillVersionListResponse
    }

    fun getSkillVersionDetail(artifactInfo: ClawHubSkillVersionInfo): ClawHubSkillVersionDetailResponse {
        val context = ArtifactQueryContext()
        return repository.query(context) as ClawHubSkillVersionDetailResponse
    }

    fun search(artifactInfo: ClawHubSearchInfo): ClawHubSearchResponse {
        val context = ArtifactSearchContext()
        val results = repository.search(context) as List<ClawHubSearchResultItem>
        return ClawHubSearchResponse(results = results)
    }

    fun download(artifactInfo: ClawHubArchiveDownloadInfo) {
        val context = ArtifactDownloadContext(useDisposition = true)
        repository.download(context)
    }

    fun getFileContent(artifactInfo: ClawHubFileDownloadInfo) {
        with(artifactInfo) {
            if (!isVersionInitialized()) {
                version = packageService.findPackageByKey(projectId, repoName, getPackageKey())?.latest
                    ?: throw PackageNotFoundException(slug)
            }
        }
        val context = ArtifactDownloadContext()
        repository.download(context)
    }

    fun publish(
        artifactInfo: ClawHubPublishInfo,
        artifactMultiFileMap: ArtifactMultiFileMap,
    ): ClawHubPublishResponse {
        with(artifactInfo) {
            val userId = SecurityUtils.getUserId()
            val artifactFile = buildSkillZip(artifactMultiFileMap["files"]!!)
            val context = ArtifactUploadContext(artifactFile = artifactFile)
            repository.upload(context)
            logger.info("User [$userId] published skill [$slug@$version] in [$projectId/$repoName]")
            return ClawHubPublishResponse(ok = true, skillId = slug, versionId = getArtifactVersion())
        }
    }

    /**
     * 限制：
     * - 文件数量不超过 [MAX_UPLOAD_FILE_COUNT]
     * - 总解压大小不超过 [MAX_UPLOAD_TOTAL_SIZE]
     */
    private fun buildSkillZip(artifactFileList: List<ArtifactFile>): ArtifactFile {
        if (artifactFileList.size > MAX_UPLOAD_FILE_COUNT) {
            throw ErrorCodeException(SkillMessageCode.SKILL_TOO_MANY_FILES, artifactFileList.size.toString())
        }
        var totalSize = 0L
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for (artifactFile in artifactFileList) {
                require(artifactFile is MultipartArtifactFile)
                totalSize += artifactFile.getSize()
                if (totalSize > MAX_UPLOAD_TOTAL_SIZE) {
                    throw ErrorCodeException(SkillMessageCode.SKILL_FILE_TOO_LARGE, MAX_UPLOAD_TOTAL_SIZE.toString())
                }
                val entryName = sanitizeEntryName(artifactFile.getOriginalFilename())
                zos.putNextEntry(ZipEntry(entryName))
                artifactFile.getInputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return ArtifactFileFactory.build(bos.toByteArray().inputStream())
    }

    /**
     * 过滤zip entry名称，防止路径遍历攻击
     */
    private fun sanitizeEntryName(name: String): String {
        return name
            .replace("\\", "/")
            .split("/")
            .filter { it != ".." && it != "." && it.isNotBlank() }
            .joinToString("/")
            .ifEmpty { "unnamed" }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClawHubService::class.java)
    }
}
