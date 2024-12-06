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

package com.tencent.bkrepo.job.separation.service.impl.repo

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.job.separation.config.DataSeparationConfig
import com.tencent.bkrepo.job.separation.dao.SeparationNodeDao
import com.tencent.bkrepo.job.separation.dao.repo.SeparationMavenMetadataDao
import com.tencent.bkrepo.job.separation.model.TSeparationNode
import com.tencent.bkrepo.job.separation.model.repo.TSeparationMavenMetadataRecord
import com.tencent.bkrepo.job.separation.pojo.RecoveryNodeInfo
import com.tencent.bkrepo.job.separation.pojo.RecoveryVersionInfo
import com.tencent.bkrepo.job.separation.pojo.VersionSeparationInfo
import com.tencent.bkrepo.job.separation.pojo.query.MavenMetadata
import com.tencent.bkrepo.job.separation.pojo.query.NodeBaseInfo
import com.tencent.bkrepo.job.separation.pojo.query.NodeDetailInfo
import com.tencent.bkrepo.job.separation.service.RepoSpecialDataSeparator
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import com.tencent.bkrepo.job.separation.util.SeparationUtils.getNodeCollectionName
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.mavenGAVC
import com.tencent.bkrepo.maven.util.MavenStringUtils.formatSeparator
import com.tencent.bkrepo.maven.util.MavenUtil.extractGroupIdAndArtifactId
import com.tencent.bkrepo.maven.util.MavenUtil.extractPath
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime


@Component
class MavenRepoSpecialDataSeparatorHandler(
    private val separationMavenMetadataDao: SeparationMavenMetadataDao,
    private val separationNodeDao: SeparationNodeDao,
    private val mongoTemplate: MongoTemplate,
    private val dataSeparationConfig: DataSeparationConfig,
) : RepoSpecialDataSeparator {

    override fun type(): RepositoryType {
        return RepositoryType.MAVEN
    }

    override fun extraType(): RepositoryType? {
        return null
    }

    override fun separateRepoSpecialData(versionSeparationInfo: VersionSeparationInfo) {
        with(versionSeparationInfo) {
            val metadataRecords = findMetaDatas(versionSeparationInfo)
            if (metadataRecords.isEmpty()) {
                logger.warn("No metadata record found for $version of $packageKey in $projectId|$repoName")
            }
            metadataRecords.forEach {
                separationMavenMetadataDao.upsertMetaData(it, separationDate)
            }
        }
    }

    override fun removeRepoSpecialData(versionSeparationInfo: VersionSeparationInfo) {
        with(versionSeparationInfo) {
            val metadataRecords = findMetaDatas(versionSeparationInfo)
            metadataRecords.forEach {
                val query = Query(Criteria.where(ID).isEqualTo(it.id))
                val deletedResult = mongoTemplate.remove(query, MAVEN_METADATA_COLLECTION_NAME)
                if (deletedResult.deletedCount != 1L) {
                    logger.error("delete metadata $it error for $version of $packageKey in $projectId|$repoName")
                }
            }
        }
    }

    override fun getNodesOfVersion(
        versionSeparationInfo: VersionSeparationInfo,
        accessCheck: Boolean
    ): MutableMap<String, String> {
        with(versionSeparationInfo) {
            val packagePath = PathUtils.normalizeFullPath(extractPath(packageKey))
            val versionPath = PathUtils.combinePath(packagePath, version)
            val nodeCollectionName = getNodeCollectionName(projectId)
            // 目录节点保持不变，空目录节点清理job会对降冷的仓库禁用
            val criteria = Criteria.where(NodeDetailInfo::projectId.name).isEqualTo(projectId)
                .and(NodeDetailInfo::repoName.name).isEqualTo(repoName)
                .and(NodeDetailInfo::deleted.name).isEqualTo(null)
                .and(NodeDetailInfo::path.name).isEqualTo(versionPath)
                .and(NodeDetailInfo::folder.name).isEqualTo(false)
            val fullPathsMap = mutableMapOf<String, String>()
            val pageSize = dataSeparationConfig.batchSize
            var querySize: Int
            var lastId = ObjectId(MIN_OBJECT_ID)
            do {
                val query = Query(criteria)
                    .addCriteria(Criteria.where(ID).gt(lastId))
                    .limit(dataSeparationConfig.batchSize)
                    .with(Sort.by(ID).ascending())
                val nodeBaseInfos = mongoTemplate.find(query, NodeBaseInfo::class.java, nodeCollectionName)
                if (nodeBaseInfos.isEmpty()) {
                    break
                }
                val nodeAccessDateCheck = nodesAccessDateCheck(accessCheck, nodeBaseInfos, separationDate)
                if (nodeAccessDateCheck) {
                    return mutableMapOf()
                }
                nodeBaseInfos.forEach {
                    fullPathsMap[it.id] = it.fullPath
                }
                querySize = nodeBaseInfos.size
                lastId = ObjectId(nodeBaseInfos.last().id)
            } while (querySize == pageSize)
            return fullPathsMap
        }
    }

    override fun getRestoreNodesOfVersion(versionSeparationInfo: VersionSeparationInfo): MutableMap<String, String> {
        with(versionSeparationInfo) {
            val packagePath = PathUtils.normalizeFullPath(extractPath(packageKey))
            val versionPath = PathUtils.combinePath(packagePath, version)
            var pageNumber = 0
            val pageSize = dataSeparationConfig.batchSize
            var querySize: Int
            val query = pathQuery(projectId, repoName, versionPath, separationDate)
            val fullPathsMap = mutableMapOf<String, String>()
            do {
                val pageQuery = query.with(PageRequest.of(pageNumber, pageSize))
                val data = separationNodeDao.find(pageQuery)
                if (data.isEmpty()) {
                    logger.warn(
                        "Could not find cold node $versionPath " +
                            "of $versionSeparationInfo in $projectId|$repoName!"
                    )
                    break
                }
                data.forEach {
                    fullPathsMap[it.id!!] = it.fullPath
                }
                querySize = data.size
                pageNumber++
            } while (querySize == pageSize)
            return fullPathsMap
        }
    }

    override fun restoreRepoSpecialData(versionSeparationInfo: VersionSeparationInfo) {
        with(versionSeparationInfo) {
            val (artifactId, groupId) = extractGroupIdAndArtifactId(packageKey)
            val coldRecord = separationMavenMetadataDao.search(
                projectId, repoName, groupId, artifactId, version, separationDate
            )
            if (coldRecord.isEmpty()) {
                logger.warn(
                    "No separation metadata record found " +
                        "for $version of $packageKey in $projectId|$repoName"
                )
            }
            restoreMetaData(coldRecord, versionSeparationInfo)
        }
    }

    override fun removeRestoredRepoSpecialData(versionSeparationInfo: VersionSeparationInfo) {
        with(versionSeparationInfo) {
            val (artifactId, groupId) = extractGroupIdAndArtifactId(packageKey)
            val coldRecord = separationMavenMetadataDao.search(
                projectId, repoName, groupId, artifactId, version, separationDate
            )
            coldRecord.forEach {
                val deletedResult = separationMavenMetadataDao.deleteById(it.id!!, it.separationDate)
                if (deletedResult.deletedCount != 1L) {
                    logger.error(
                        "delete separation metadata $it error for $version of $packageKey" +
                            " in $projectId|$repoName"
                    )
                }
            }
        }
    }

    override fun getRecoveryPackageVersionData(recoveryInfo: RecoveryNodeInfo): RecoveryVersionInfo {
        with(recoveryInfo) {
            val mavenGAVC = fullPath.mavenGAVC()
            val version = mavenGAVC.version
            val artifactId = mavenGAVC.artifactId
            val groupId = mavenGAVC.groupId.formatSeparator("/", ".")
            val packageKey = PackageKeys.ofGav(groupId, artifactId)
            return RecoveryVersionInfo(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                version = version
            )
        }
    }

    private fun pathQuery(
        projectId: String, repoName: String, versionPath: String, separationDate: LocalDateTime
    ): Query {
        val (startOfDay, endOfDay) = SeparationUtils.findStartAndEndTimeOfDate(separationDate)
        val criteria = Criteria.where(TSeparationNode::projectId.name).isEqualTo(projectId)
            .and(TSeparationNode::repoName.name).isEqualTo(repoName)
            .and(TSeparationNode::path.name).isEqualTo(versionPath)
            .and(TSeparationNode::folder.name).isEqualTo(false)
            .and(TSeparationNode::separationDate.name).gte(startOfDay).lt(endOfDay)
        return Query(criteria)
    }

    /**
     * 只要该版本下有一个文件不符合条件则该版本不能降冷
     */
    private fun nodesAccessDateCheck(
        accessCheck: Boolean,
        nodeBaseInfos: List<NodeBaseInfo>,
        separationDate: LocalDateTime
    ): Boolean {
        if (!accessCheck) return false
        return nodeBaseInfos.firstOrNull {
            it.lastAccessDate?.isAfter(separationDate) == true || it.lastModifiedDate.isAfter(separationDate)
        } != null
    }

    private fun findMetaDatas(versionSeparationInfo: VersionSeparationInfo): List<MavenMetadata> {
        with(versionSeparationInfo) {
            val (artifactId, groupId) = extractGroupIdAndArtifactId(packageKey)
            val criteria = Criteria.where(MavenMetadata::projectId.name).isEqualTo(projectId)
                .and(MavenMetadata::repoName.name).isEqualTo(repoName)
                .and(MavenMetadata::groupId.name).isEqualTo(groupId)
                .and(MavenMetadata::artifactId.name).isEqualTo(artifactId)
                .and(MavenMetadata::version.name).isEqualTo(version)
            val metadataQuery = Query(criteria)
            return mongoTemplate.find(
                metadataQuery,
                MavenMetadata::class.java,
                MAVEN_METADATA_COLLECTION_NAME
            )
        }
    }

    private fun restoreMetaData(
        coldRecord: List<TSeparationMavenMetadataRecord>,
        versionSeparationInfo: VersionSeparationInfo
    ) {
        coldRecord.forEach {
            val criteria = Criteria.where(MavenMetadata::projectId.name).isEqualTo(it.projectId)
                .and(MavenMetadata::repoName.name).isEqualTo(it.repoName)
                .and(MavenMetadata::groupId.name).isEqualTo(it.groupId)
                .and(MavenMetadata::artifactId.name).isEqualTo(it.artifactId)
                .and(MavenMetadata::version.name).isEqualTo(it.version)
                .and(MavenMetadata::classifier.name).isEqualTo(it.classifier)
                .and(MavenMetadata::extension.name).isEqualTo(it.extension)
            val existQuery = Query(criteria)
            val hotRecord = mongoTemplate.find(
                existQuery,
                MavenMetadata::class.java, MAVEN_METADATA_COLLECTION_NAME
            )
            if (hotRecord.isEmpty() || versionSeparationInfo.overwrite) {
                val update = Update().set(MavenMetadata::buildNo.name, it.buildNo)
                    .set(MavenMetadata::timestamp.name, it.timestamp)
                mongoTemplate.upsert(existQuery, update, MAVEN_METADATA_COLLECTION_NAME)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MavenRepoSpecialDataSeparatorHandler::class.java)
        private const val MAVEN_METADATA_COLLECTION_NAME = "maven_metadata"
    }
}
