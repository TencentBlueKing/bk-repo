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

package com.tencent.bkrepo.job.separation.service.impl

import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PACKAGE_COLLECTION_NAME
import com.tencent.bkrepo.job.PACKAGE_DOWNLOADS_COLLECTION_NAME
import com.tencent.bkrepo.job.PACKAGE_DOWNLOAD_DATE
import com.tencent.bkrepo.job.PACKAGE_KEY
import com.tencent.bkrepo.job.PACKAGE_VERSION
import com.tencent.bkrepo.job.PACKAGE_VERSION_COLLECTION_NAME
import com.tencent.bkrepo.job.separation.dao.SeparationFailedRecordDao
import com.tencent.bkrepo.job.separation.dao.SeparationNodeDao
import com.tencent.bkrepo.job.separation.dao.SeparationPackageDao
import com.tencent.bkrepo.job.separation.dao.SeparationPackageVersionDao
import com.tencent.bkrepo.job.separation.exception.SeparationDataNotFoundException
import com.tencent.bkrepo.job.separation.model.TSeparationNode
import com.tencent.bkrepo.job.separation.model.TSeparationPackage
import com.tencent.bkrepo.job.separation.model.TSeparationPackageVersion
import com.tencent.bkrepo.job.separation.pojo.NodeFilterInfo
import com.tencent.bkrepo.job.separation.pojo.PackageFilterInfo
import com.tencent.bkrepo.job.separation.pojo.SeparationArtifactType
import com.tencent.bkrepo.job.separation.pojo.VersionFilterInfo
import com.tencent.bkrepo.job.separation.pojo.VersionSeparationInfo
import com.tencent.bkrepo.job.separation.pojo.query.NodeBaseInfo
import com.tencent.bkrepo.job.separation.pojo.query.NodeDetailInfo
import com.tencent.bkrepo.job.separation.pojo.query.PackageDetailInfo
import com.tencent.bkrepo.job.separation.pojo.query.PackageInfo
import com.tencent.bkrepo.job.separation.pojo.query.PackageVersionInfo
import com.tencent.bkrepo.job.separation.pojo.query.VersionDetailInfo
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.service.DataSeparator
import com.tencent.bkrepo.job.separation.service.impl.repo.RepoSpecialSeparationMappings
import com.tencent.bkrepo.job.separation.util.SeparationUtils.getNodeCollectionName
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class DataSeparatorImpl(
    private val mongoTemplate: MongoTemplate,
    private val separationPackageVersionDao: SeparationPackageVersionDao,
    private val separationPackageDao: SeparationPackageDao,
    private val separationFailedRecordDao: SeparationFailedRecordDao,
    private val separationNodeDao: SeparationNodeDao,
) : AbstractHandler(mongoTemplate, separationFailedRecordDao), DataSeparator {
    override fun repoSeparator(context: SeparationContext) {
        logger.info("start to do separation for repo ${context.projectId}|${context.repoName}")
        when (context.separationArtifactType) {
            SeparationArtifactType.PACKAGE -> handlePackageSeparation(context)
            else -> handleNodeSeparation(context)
        }
        logger.info("separation for repo ${context.projectId}|${context.repoName} finished")
    }

    override fun packageSeparator(context: SeparationContext, pkg: PackageFilterInfo) {
        logger.info("start to do separation for package $pkg in repo ${context.projectId}|${context.repoName}")
        handlePackageSeparation(context, pkg)
        logger.info("separation for package $pkg in repo ${context.projectId}|${context.repoName} finished")
    }

    override fun versionSeparator(context: SeparationContext, version: VersionFilterInfo) {
        logger.info("start to do separation for version $version in repo ${context.projectId}|${context.repoName}")
        val pkg = PackageFilterInfo(
            packageName = version.packageName,
            versions = version.versions,
            versionRegex = version.versionRegex
        )
        handlePackageSeparation(context, pkg)
        logger.info("separation for version $version in repo ${context.projectId}|${context.repoName} finished")
    }

    override fun nodeSeparator(context: SeparationContext, node: NodeFilterInfo) {
        logger.info("start to do separation for node $node in repo ${context.projectId}|${context.repoName}")
        handleNodeSeparation(context, node)
        logger.info("separation for node $node in repo ${context.projectId}|${context.repoName} finished")
    }

    private fun handlePackageSeparation(
        context: SeparationContext, pkg: PackageFilterInfo? = null,
    ) {
        with(context) {
            validatePackageParams(pkg)
            val criteria = buildPackageCriteria(projectId, repoName, separationDate, pkg)
            val pageSize = BATCH_SIZE
            var querySize: Int
            var lastId = ObjectId(MIN_OBJECT_ID)
            do {
                val query = Query(criteria)
                    .addCriteria(Criteria.where(ID).gt(lastId))
                    .limit(BATCH_SIZE)
                    .with(Sort.by(ID).ascending())
                val data = mongoTemplate.find(query, PackageInfo::class.java, PACKAGE_COLLECTION_NAME)
                if (data.isEmpty()) {
                    break
                }
                data.forEach {
                    handleVersionSeparation(context, pkg, it)
                }
                querySize = data.size
                lastId = ObjectId(data.last().id)
            } while (querySize == pageSize)
        }
    }

    private fun buildPackageCriteria(
        projectId: String, repoName: String,
        separationDate: LocalDateTime, pkg: PackageFilterInfo?
    ): Criteria {
        val criteria = Criteria.where(PROJECT_ID).isEqualTo(projectId)
            .and(REPO_NAME).isEqualTo(repoName)
            .and(LAST_MODIFIED_DATE).lte(separationDate)
        if (pkg != null) {
            if (pkg.packageName.isNullOrEmpty()) {
                if (!pkg.packageRegex.isNullOrEmpty()) {
                    criteria.and(NAME).regex(".*${pkg.packageRegex}.*")
                }
            } else {
                criteria.and(NAME).isEqualTo(pkg.packageName)
            }
        }
        return criteria
    }

    private fun handleVersionSeparation(
        context: SeparationContext,
        pkg: PackageFilterInfo?,
        packageInfo: PackageInfo,
    ) {
        with(context) {
            val criteria = buildVersionCriteria(pkg, packageInfo, separationDate)
            val pageSize = BATCH_SIZE
            var querySize: Int
            var lastId = ObjectId(MIN_OBJECT_ID)
            do {
                val query = Query(criteria)
                    .addCriteria(Criteria.where(ID).gt(lastId))
                    .limit(BATCH_SIZE)
                    .with(Sort.by(ID).ascending())
                val data = mongoTemplate.find(query, PackageVersionInfo::class.java, PACKAGE_VERSION_COLLECTION_NAME)
                if (data.isEmpty()) {
                    break
                }
                data.forEach {
                    searchVersionDownloadRecord(
                        context, packageInfo, it
                    )
                }
                querySize = data.size
                lastId = ObjectId(data.last().id)
            } while (querySize == pageSize)
        }
    }

    private fun buildVersionCriteria(
        pkg: PackageFilterInfo?,
        packageInfo: PackageInfo,
        separationDate: LocalDateTime
    ): Criteria {
        val criteria = Criteria.where(PackageVersionInfo::packageId.name).isEqualTo(packageInfo.id)
            .and(LAST_MODIFIED_DATE).lte(separationDate)
        if (pkg != null) {
            if (pkg.versions.isNullOrEmpty()) {
                if (!pkg.versionRegex.isNullOrEmpty()) {
                    criteria.and(NAME).regex(".*${pkg.versionRegex}.*")
                }
            } else {
                criteria.and(NAME).`in`(pkg.versions)
            }
        }
        return criteria
    }

    private fun searchVersionDownloadRecord(
        context: SeparationContext,
        packageInfo: PackageInfo,
        packageVersionInfo: PackageVersionInfo,
    ) {
        with(context) {
            val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val date = separationDate.format(dateTimeFormatter)
            val criteria = Criteria.where(PROJECT_ID).isEqualTo(projectId)
                .and(REPO_NAME).isEqualTo(repoName)
                .and(PACKAGE_KEY).isEqualTo(packageInfo.key)
                .and(PACKAGE_VERSION).isEqualTo(packageVersionInfo.name)
                .and(PACKAGE_DOWNLOAD_DATE).gt(date)
            val query = Query(criteria)
                .limit(1)
                .with(Sort.by(PACKAGE_DOWNLOAD_DATE).descending())
            val result = mongoTemplate.find<Map<String, Any?>>(query, PACKAGE_DOWNLOADS_COLLECTION_NAME)
            if (result.isNotEmpty()) {
                return
            }
            copyColdData(context, packageInfo, packageVersionInfo)
        }
    }

    private fun copyColdData(
        context: SeparationContext, packageInfo: PackageInfo,
        packageVersionInfo: PackageVersionInfo,
    ) {
        with(context) {
            try {
                val versionSeparationInfo = VersionSeparationInfo(
                    projectId = projectId,
                    repoName = repoName,
                    type = repoType,
                    packageKey = packageInfo.key,
                    version = packageVersionInfo.name,
                    separationDate = separationDate,
                )
                logger.info("start to copy cold data $versionSeparationInfo in repo $projectId|$repoName")
                // 查找出版本对应节点
                val nodeRecordsMap = RepoSpecialSeparationMappings.getNodesOfVersion(versionSeparationInfo)
                if (nodeRecordsMap.isEmpty()) {
                    logger.warn(
                        "nodes of package ${packageInfo.key} with version ${packageVersionInfo.name} " +
                            "has been accessed after $separationDate"
                    )
                    return
                }
                //  copy到冷表, 并增加引用
                val idSha256Map = separateColdNodes(context, nodeRecordsMap)
                // copy对应依赖源特殊数据
                RepoSpecialSeparationMappings.separateRepoColdData(versionSeparationInfo)
                // copy package以及version信息
                copyColdPackageVersion(context, packageInfo, packageVersionInfo)
                //删除package, 逻辑删除node,
                removeColdDataInSource(context, idSha256Map, packageInfo, packageVersionInfo)
                setSuccessProgress(context.separationProgress, packageInfo.id, packageVersionInfo.id)
                if (context.fixTask) {
                    removeFailedRecord(context, packageInfo.id, packageVersionInfo.id)
                }
            } catch (e: Exception) {
                logger.error(
                    "copy cold package ${packageInfo.key} with version ${packageVersionInfo.name}" +
                        " failed, error: ${e.message}"
                )
                saveFailedRecord(
                    context, packageInfo.id, packageVersionInfo.id,
                )
                setFailedProgress(context.separationProgress, packageInfo.id, packageVersionInfo.id)
            }
        }
    }

    private fun copyColdPackageVersion(
        context: SeparationContext, packageInfo: PackageInfo,
        packageVersionInfo: PackageVersionInfo,
    ) {
        // 先复制Package信息， 防止Package被重复创建存在多个
        val packageId = storeColdPackage(context, packageInfo)
        storeColdPackageVersion(context, packageId, packageVersionInfo)
    }

    private fun removeColdDataInSource(
        context: SeparationContext, idSha256Map: MutableMap<String, String>,
        packageInfo: PackageInfo? = null, packageVersionInfo: PackageVersionInfo? = null,
    ) {
        if (packageInfo != null && packageVersionInfo != null) {
            removeColdVersionFromSource(context, packageInfo, packageVersionInfo)
        }
        // 逻辑删除节点
        val collectionName = getNodeCollectionName(context.projectId)
        removeColdNodeFromSource(context, collectionName, idSha256Map)
    }

    private fun storeColdPackageVersion(
        context: SeparationContext, packageId: String,
        packageVersionInfo: PackageVersionInfo,
    ) {
        val versionQuery = Query(Criteria.where(ID).isEqualTo(packageVersionInfo.id))
        val coldVersionRecord = mongoTemplate.findOne(
            versionQuery,
            VersionDetailInfo::class.java, PACKAGE_VERSION_COLLECTION_NAME
        )
        if (coldVersionRecord == null) {
            logger.error(
                "copy separation version $packageVersionInfo" +
                    " failed in ${context.projectId}|${context.repoName}"
            )
            throw SeparationDataNotFoundException(packageVersionInfo.id)
        }
        // 防止Package降冷后再创建，存在多个，以第一次降冷的为主
        coldVersionRecord.packageId = packageId
        // 检查版本是否存在
        val oldVersion = separationPackageVersionDao.findByName(
            packageId, packageVersionInfo.name, context.separationDate
        )
        if (oldVersion != null) {
            oldVersion.apply {
                lastModifiedBy = coldVersionRecord.lastModifiedBy
                lastModifiedDate = coldVersionRecord.lastModifiedDate
                size = coldVersionRecord.size
                ordinal = coldVersionRecord.ordinal
                downloads = coldVersionRecord.downloads
                manifestPath = coldVersionRecord.manifestPath
                artifactPath = coldVersionRecord.artifactPath
                stageTag = coldVersionRecord.stageTag
                metadata = coldVersionRecord.metadata
                tags = coldVersionRecord.tags
                extension = coldVersionRecord.extension
                clusterNames = coldVersionRecord.clusterNames
            }
            separationPackageVersionDao.save(oldVersion)
            logger.info(
                "update separation package version[${oldVersion.packageId}|${oldVersion.name}] " +
                    "success in ${context.projectId}|${context.repoName}"
            )
        } else {
            // create cold
            val coldVersion = buildTSeparationPackageVersion(coldVersionRecord, context.separationDate)
            separationPackageVersionDao.save(coldVersion)
            logger.info(
                "separation package version[${coldVersion.packageId}|${coldVersion.name}] " +
                    "success in ${context.projectId}|${context.repoName}"
            )
        }
    }

    private fun storeColdPackage(context: SeparationContext, packageInfo: PackageInfo): String {
        val packageQuery = Query(Criteria.where(ID).isEqualTo(packageInfo.id))
        val coldPackageRecord = mongoTemplate.findOne(
            packageQuery, PackageDetailInfo::class.java, PACKAGE_COLLECTION_NAME
        )
        if (coldPackageRecord == null) {
            logger.error("separation package $packageInfo has been deleted in ${context.projectId}|${context.repoName}")
            throw SeparationDataNotFoundException(packageInfo.id)
        }
        var existColdPackage = separationPackageDao.findByKey(context.projectId, context.repoName, packageInfo.key)
        if (existColdPackage == null) {
            existColdPackage = buildTSeparationPackage(coldPackageRecord, context.separationDate)
            separationPackageDao.save(existColdPackage)
        } else {
            // 防止同一Package有多个，以历史降冷的Package信息为准
            if (existColdPackage.id != coldPackageRecord.id) {
                existColdPackage.apply {
                    lastModifiedBy = coldPackageRecord.lastModifiedBy
                    lastModifiedDate = coldPackageRecord.lastModifiedDate
                    downloads = coldPackageRecord.downloads
                    versions = coldPackageRecord.versions
                    versionTag = coldPackageRecord.versionTag
                    extension = coldPackageRecord.extension
                    description = coldPackageRecord.description
                    historyVersion = coldPackageRecord.historyVersion
                    clusterNames = coldPackageRecord.clusterNames
                    latest = coldPackageRecord.latest
                }
                separationPackageDao.save(existColdPackage)
            }
        }
        logger.info(
            "separation cold package[${existColdPackage.id}] success" +
                " in ${context.projectId}|${context.repoName}"
        )
        return existColdPackage.id!!
    }

    private fun removeColdVersionFromSource(
        context: SeparationContext, packageInfo: PackageInfo,
        packageVersionInfo: PackageVersionInfo,
    ) {
        val deletedResult = mongoTemplate.remove(
            Criteria.where(ID).isEqualTo(packageVersionInfo.id), PACKAGE_VERSION_COLLECTION_NAME
        )
        if (deletedResult.deletedCount != 1L) {
            logger.error(
                "delete version $packageVersionInfo error " +
                    "for $packageInfo in ${context.projectId}|${context.repoName}"
            )
        } else {
            logger.info(
                "delete version $packageVersionInfo success " +
                    "for $packageInfo in ${context.projectId}|${context.repoName}"
            )
        }
    }

    private fun separateColdNodes(
        context: SeparationContext, fullPaths: MutableMap<String, String>
    ): MutableMap<String, String> {
        logger.info("start to copy cold node in repo ${context.projectId}|${context.repoName}")
        val idSha256Map = mutableMapOf<String, String>()
        fullPaths.forEach {
            val (id, sha256) = separateColdNode(context, it.value, it.key)
            idSha256Map[id] = sha256
        }
        return idSha256Map
    }

    private fun separateColdNode(context: SeparationContext, fullPath: String, id: String): Pair<String, String> {
        with(context) {
            val nodeCollectionName = getNodeCollectionName(projectId)
            val nodeQuery = Query(Criteria.where(ID).isEqualTo(id))
            val nodeRecord = mongoTemplate.findOne(nodeQuery, NodeDetailInfo::class.java, nodeCollectionName)
            if (nodeRecord == null) {
                logger.error(
                    "copy separation node $id with " +
                        "fullPath $fullPath failed in $projectId|$repoName"
                )
                throw SeparationDataNotFoundException(id)
            }
            // 存储冷数据
            storeColdNode(context, nodeRecord)
            return Pair(nodeRecord.id!!, nodeRecord.sha256!!)
        }
    }

    private fun storeColdNode(context: SeparationContext, nodeRecord: NodeDetailInfo) {
        with(context) {
            val existCodeNodeQuery = Query(
                Criteria.where(TSeparationNode::projectId.name).isEqualTo(projectId)
                    .and(TSeparationNode::repoName.name).isEqualTo(repoName)
                    .and(TSeparationNode::fullPath.name).isEqualTo(nodeRecord.fullPath)
                    .and(TSeparationNode::separationDate.name).isEqualTo(separationDate)
            )
            val existedCodeNode = separationNodeDao.findOne(existCodeNodeQuery)
            // 插入前判断cold node 是否存在
            val storedColdNode: TSeparationNode
            if (existedCodeNode != null) {
                existedCodeNode.apply {
                    expireDate = nodeRecord.expireDate
                    size = nodeRecord.size
                    sha256 = nodeRecord.sha256
                    md5 = nodeRecord.md5
                    nodeNum = nodeRecord.nodeNum
                    metadata = nodeRecord.metadata
                    lastModifiedBy = nodeRecord.lastModifiedBy
                    lastModifiedDate = nodeRecord.lastModifiedDate
                    lastAccessDate = nodeRecord.lastAccessDate
                    copyFromCredentialsKey = nodeRecord.copyFromCredentialsKey
                    copyIntoCredentialsKey = nodeRecord.copyIntoCredentialsKey
                    clusterNames = nodeRecord.clusterNames
                    archived = nodeRecord.archived
                    compressed = nodeRecord.compressed
                }
                storedColdNode = separationNodeDao.save(existedCodeNode)
                logger.info("Update separation node ${nodeRecord.fullPath} success in $projectId|$repoName")
            } else {
                val codeNode = buildTSeparationNode(nodeRecord, separationDate)
                storedColdNode = separationNodeDao.save(codeNode)
                logger.info("Create separation node ${nodeRecord.fullPath} success in $projectId|$repoName")
            }
            if (!sha256Check(nodeRecord.folder, nodeRecord.sha256)) {
                logger.warn("store code node[${nodeRecord.id}] sha256 is null or blank.")
                return
            }
            increment(storedColdNode.sha256!!, credentialsKey, 1)
        }
    }

    private fun removeColdNodeFromSource(
        context: SeparationContext,
        nodeCollectionName: String,
        idSha256Map: MutableMap<String, String>
    ) {
        with(context) {
            idSha256Map.forEach {
                val nodeQuery = Query(Criteria.where(ID).isEqualTo(it.key))
                // 逻辑删除， 同时删除索引
                val update = Update()
                    .set(NodeDetailInfo::lastModifiedBy.name, SYSTEM_USER)
                    .set(NodeDetailInfo::deleted.name, LocalDateTime.now())
                val updateResult = mongoTemplate.updateFirst(nodeQuery, update, nodeCollectionName)
                if (updateResult.modifiedCount != 1L) {
                    logger.error("delete hot node failed with id ${it.key} in $projectId|$repoName")
                }
            }
        }
    }

    private fun handleNodeSeparation(context: SeparationContext, node: NodeFilterInfo? = null) {
        with(context) {
            validateNodeParams(node)
            val criteria = buildNodeCriteria(context, node)
            val collectionName = getNodeCollectionName(projectId)
            val pageSize = BATCH_SIZE
            var querySize: Int
            var lastId = ObjectId(MIN_OBJECT_ID)
            do {
                val query = Query(criteria)
                    .addCriteria(Criteria.where(ID).gt(lastId))
                    .limit(BATCH_SIZE)
                    .with(Sort.by(ID).ascending())
                val data = mongoTemplate.find(query, NodeBaseInfo::class.java, collectionName)
                if (data.isEmpty()) {
                    break
                }
                data.forEach {
                    separateColdNode(context, it)
                }
                querySize = data.size
                lastId = ObjectId(data.last().id)
            } while (querySize == pageSize)
        }
    }

    private fun separateColdNode(context: SeparationContext, node: NodeBaseInfo) {
        try {
            val (id, sha256) = separateColdNode(context, node.fullPath, node.id)
            // 逻辑删除node,
            removeColdDataInSource(context, mutableMapOf(id to sha256))
            setSuccessProgress(context.separationProgress, nodeId = node.id)
            if (context.fixTask) {
                removeFailedRecord(context, nodeId = node.id)
            }
        } catch (e: Exception) {
            logger.error(
                "copy cold node ${node.id} with path ${node.fullPath}" +
                    " failed, error: ${e.message}"
            )
            saveFailedRecord(
                context, nodeId = node.id
            )
            setFailedProgress(context.separationProgress, nodeId = node.id)
        }
    }

    private fun buildNodeCriteria(context: SeparationContext, node: NodeFilterInfo? = null): Criteria {
        with(context) {
            val criteria = where(NodeDetailInfo::projectId).isEqualTo(projectId)
                .and(NodeDetailInfo::repoName).isEqualTo(repoName)
                .and(NodeDetailInfo::deleted).isEqualTo(null)
                .and(NodeDetailInfo::folder).isEqualTo(false)
                .orOperator(
                    Criteria().and(NodeDetailInfo::lastAccessDate).lt(separationDate)
                        .and(NodeDetailInfo::lastModifiedDate).lt(separationDate),
                    Criteria().and(NodeDetailInfo::lastAccessDate).`is`(null)
                        .and(NodeDetailInfo::lastModifiedDate).lt(separationDate),
                )
            if (node == null) return criteria
            if (node.path.isNullOrEmpty()) {
                if (!node.pathRegex.isNullOrEmpty()) {
                    criteria.and(NodeDetailInfo::fullPath).regex(".*${node.pathRegex}.*")
                }
            } else {
                val nodePath = PathUtils.toPath(node.path)
                criteria.and(NodeDetailInfo::fullPath).regex("^${PathUtils.escapeRegex(nodePath)}")
            }
            return criteria
        }
    }

    private fun buildTSeparationNode(node: NodeDetailInfo, separationDate: LocalDateTime): TSeparationNode {
        return TSeparationNode(
            projectId = node.projectId,
            repoName = node.repoName,
            path = node.path,
            name = node.name,
            fullPath = node.fullPath,
            folder = node.folder,
            expireDate = node.expireDate,
            size = node.size,
            sha256 = node.sha256,
            md5 = node.md5,
            nodeNum = node.nodeNum,
            metadata = node.metadata,
            createdBy = node.createdBy,
            createdDate = node.createdDate,
            lastModifiedBy = node.lastModifiedBy,
            lastModifiedDate = node.lastModifiedDate,
            lastAccessDate = node.lastAccessDate,
            copyFromCredentialsKey = node.copyFromCredentialsKey,
            copyIntoCredentialsKey = node.copyIntoCredentialsKey,
            clusterNames = node.clusterNames,
            archived = node.archived,
            compressed = node.compressed,
            separationDate = separationDate
        )
    }


    private fun buildTSeparationPackage(
        packageDetail: PackageDetailInfo, separationDate: LocalDateTime
    ): TSeparationPackage {
        return TSeparationPackage(
            id = packageDetail.id,
            createdBy = packageDetail.createdBy,
            createdDate = packageDetail.createdDate,
            lastModifiedBy = packageDetail.lastModifiedBy,
            lastModifiedDate = packageDetail.lastModifiedDate,
            projectId = packageDetail.projectId,
            repoName = packageDetail.repoName,
            name = packageDetail.name,
            key = packageDetail.key,
            type = packageDetail.type,
            downloads = packageDetail.downloads,
            versions = packageDetail.versions,
            versionTag = packageDetail.versionTag,
            extension = packageDetail.extension,
            description = packageDetail.description,
            historyVersion = packageDetail.historyVersion,
            separationDate = separationDate
        )
    }

    private fun buildTSeparationPackageVersion(
        versionDetail: VersionDetailInfo, separationDate: LocalDateTime
    ): TSeparationPackageVersion {
        return TSeparationPackageVersion(
            createdBy = versionDetail.createdBy,
            createdDate = versionDetail.createdDate,
            lastModifiedBy = versionDetail.lastModifiedBy,
            lastModifiedDate = versionDetail.lastModifiedDate,
            packageId = versionDetail.packageId,
            name = versionDetail.name,
            size = versionDetail.size,
            ordinal = versionDetail.ordinal,
            downloads = versionDetail.downloads,
            manifestPath = versionDetail.manifestPath,
            artifactPath = versionDetail.artifactPath,
            stageTag = versionDetail.stageTag,
            metadata = versionDetail.metadata,
            tags = versionDetail.tags,
            extension = versionDetail.extension,
            clusterNames = versionDetail.clusterNames,
            separationDate = separationDate
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataSeparatorImpl::class.java)
    }
}