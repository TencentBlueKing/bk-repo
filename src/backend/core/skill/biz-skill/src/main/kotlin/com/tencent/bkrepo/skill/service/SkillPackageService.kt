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

import com.tencent.bkrepo.common.artifact.constant.MD5
import com.tencent.bkrepo.common.artifact.constant.SHA256
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.skill.constant.FIELD_CHANGELOG
import com.tencent.bkrepo.skill.constant.FIELD_DISPLAY_NAME
import com.tencent.bkrepo.skill.constant.KEY_FILE_LIST
import com.tencent.bkrepo.skill.constant.KEY_FINGERPRINT
import com.tencent.bkrepo.skill.constant.KEY_SKILL_MD
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubArchiveDownloadInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubPublishInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SkillPackageService(
    private val packageService: PackageService,
) {

    fun createVersion(
        artifactInfo: ClawHubPublishInfo,
        size: Long,
        sha256: String,
        md5: String,
    ) {
        with(artifactInfo) {
            val userId = SecurityUtils.getUserId()
            val version = getArtifactVersion()
            val versionExtension = mutableMapOf(
                SHA256 to sha256,
                MD5 to md5,
                FIELD_DISPLAY_NAME to payload.displayName,
                FIELD_CHANGELOG to payload.changelog,
                KEY_FILE_LIST to fileList,
                KEY_FINGERPRINT to fingerprint,
            )
            if (skillMdContent != null) versionExtension[KEY_SKILL_MD] = skillMdContent
            val versionCreateRequest = PackageVersionCreateRequest(
                projectId = projectId,
                repoName = repoName,
                packageName = slug,
                packageKey = getPackageKey(),
                packageType = PackageType.SKILL,
                packageDescription = metadata?.description,
                packageExtension = mapOf(FIELD_DISPLAY_NAME to payload.displayName),
                versionName = version,
                size = size,
                artifactPath = getArtifactFullPath(),
                packageMetadata = listOf(
                    MetadataModel(key = FIELD_DISPLAY_NAME, value = payload.displayName, system = true)
                ),
                tags = payload.tags,
                extension = versionExtension,
                createdBy = userId,
            )
            packageService.createPackageVersion(versionCreateRequest, HttpContextHolder.getClientAddress())
            logger.info("User [$userId] created version [$version] for skill [$slug] in [$projectId/$repoName]")
        }
    }

    fun buildDownloadRecord(artifactInfo: ClawHubArchiveDownloadInfo): PackageDownloadRecord? {
        with(artifactInfo) {
            return PackageDownloadRecord(
                projectId = projectId,
                repoName = repoName,
                packageKey = getPackageKey(),
                packageVersion = version,
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SkillPackageService::class.java)
    }
}
