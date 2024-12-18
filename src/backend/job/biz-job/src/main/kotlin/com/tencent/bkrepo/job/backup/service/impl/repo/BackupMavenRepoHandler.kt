/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.backup.service.impl.repo

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.job.backup.pojo.query.BackupMavenMetadata
import com.tencent.bkrepo.job.backup.pojo.query.BackupNodeInfo
import com.tencent.bkrepo.job.backup.pojo.query.VersionBackupInfo
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.service.BackupRepoSpecialDataService
import com.tencent.bkrepo.job.backup.service.impl.BaseService
import com.tencent.bkrepo.maven.util.MavenUtil
import com.tencent.bkrepo.maven.util.MavenUtil.extractPath
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component


@Component
class BackupMavenRepoHandler(
    private val mongoTemplate: MongoTemplate,
) : BackupRepoSpecialDataService, BaseService() {

    override fun type(): RepositoryType {
        return RepositoryType.MAVEN
    }

    override fun extraType(): RepositoryType? {
        return null
    }

    override fun getNodeCriteriaOfVersion(versionBackupInfo: VersionBackupInfo): Criteria {
        with(versionBackupInfo) {
            val packagePath = PathUtils.normalizeFullPath(extractPath(packageKey))
            val versionPath = PathUtils.combinePath(packagePath, version)
            return Criteria.where(BackupNodeInfo::path.name).isEqualTo(versionPath)
        }
    }

    override fun storeRepoSpecialData(versionBackupInfo: VersionBackupInfo, context: BackupContext) {
        with(versionBackupInfo) {
            val metadataRecords = findMetadata(versionBackupInfo)
            if (metadataRecords.isEmpty()) {
                logger.warn("No metadata record found for $version of $packageKey in $projectId|$repoName")
                return
            }
            metadataRecords.forEach {
                storeData(it, context)
            }
        }
    }

    private fun findMetadata(versionBackupInfo: VersionBackupInfo): List<BackupMavenMetadata> {
        with(versionBackupInfo) {
            val (artifactId, groupId) = MavenUtil.extractGroupIdAndArtifactId(packageKey)
            val criteria = Criteria.where(BackupMavenMetadata::projectId.name).isEqualTo(projectId)
                .and(BackupMavenMetadata::repoName.name).isEqualTo(repoName)
                .and(BackupMavenMetadata::groupId.name).isEqualTo(groupId)
                .and(BackupMavenMetadata::artifactId.name).isEqualTo(artifactId)
                .and(BackupMavenMetadata::version.name).isEqualTo(version)
            val metadataQuery = Query(criteria)
            return mongoTemplate.find(
                metadataQuery,
                BackupMavenMetadata::class.java,
                MAVEN_METADATA_COLLECTION_NAME
            )
        }
    }


    companion object {
        private val logger = LoggerFactory.getLogger(BackupMavenRepoHandler::class.java)
    }
}
