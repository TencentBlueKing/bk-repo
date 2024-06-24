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

import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.separation.constant.PACKAGE_COLLECTION_NAME
import com.tencent.bkrepo.job.separation.constant.PACKAGE_VERSION_COLLECTION_NAME
import com.tencent.bkrepo.job.separation.constant.RESTORE
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
import com.tencent.bkrepo.job.separation.pojo.query.NodeDetailInfo
import com.tencent.bkrepo.job.separation.pojo.query.PackageDetailInfo
import com.tencent.bkrepo.job.separation.pojo.query.VersionDetailInfo
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.service.DataRestorer
import com.tencent.bkrepo.job.separation.service.impl.repo.RepoSpecialSeparationMappings
import com.tencent.bkrepo.job.separation.util.SeparationQueryHelper
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class DataRestorerImpl(
    private val mongoTemplate: MongoTemplate,
    private val separationPackageVersionDao: SeparationPackageVersionDao,
    private val separationPackageDao: SeparationPackageDao,
    private val separationFailedRecordDao: SeparationFailedRecordDao,
    private val separationNodeDao: SeparationNodeDao
) : AbstractHandler(mongoTemplate), DataRestorer {
    override fun repoRestorer(context: SeparationContext) {
        logger.info("start to do restore for repo ${context.projectId}|${context.repoName}")
        when (context.separationArtifactType) {
            SeparationArtifactType.PACKAGE -> handlePackageRestore(context)
            else -> handleNodeRestore(context)
        }
        logger.info("restore for repo ${context.projectId}|${context.repoName} finished")
    }

    override fun packageRestorer(context: SeparationContext, pkg: PackageFilterInfo) {
        logger.info("start to do restore for package $pkg in repo ${context.projectId}|${context.repoName}")
        handlePackageRestore(context, pkg)
        logger.info("restore for package $pkg in repo ${context.projectId}|${context.repoName} finished")
    }

    override fun versionRestorer(context: SeparationContext, version: VersionFilterInfo) {
        logger.info("start to do restore for version $version in repo ${context.projectId}|${context.repoName}")
        val pkg = PackageFilterInfo(
            packageName = version.packageName,
            versions = version.versions,
            versionRegex = version.versionRegex
        )
        handlePackageRestore(context, pkg)
        logger.info("restore for version $version in repo ${context.projectId}|${context.repoName} finished")
    }

    override fun nodeRestorer(context: SeparationContext, node: NodeFilterInfo) {
        logger.info("start to do restore for node $node in repo ${context.projectId}|${context.repoName}")
        handleNodeRestore(context, node)
        logger.info("restore for node $node in repo ${context.projectId}|${context.repoName} finished")
    }


    private fun handlePackageRestore(context: SeparationContext, pkg: PackageFilterInfo? = null) {
        // TODO 需要支持恢复指定降冷日期内的数据
        with(context) {
            validatePackageParams(pkg)
            if (context.restoreDates.isNullOrEmpty())
                throw NotFoundException(
                    CommonMessageCode.REQUEST_CONTENT_INVALID,
                    "no separation task for $projectId|$repoName"
                )
            var pageNumber = 0
            val pageSize = BATCH_SIZE
            var querySize = 0
            val query = SeparationQueryHelper.packageNameQuery(projectId, repoName, pkg?.packageName, pkg?.packageRegex)

            do {
                val pageQuery = query.with(PageRequest.of(pageNumber, pageSize))
                val data = separationPackageDao.find(pageQuery)
                if (data.isEmpty()) {
                    logger.warn("Could not find cold package $pkg in $projectId|$repoName !")
                    break
                }
                data.forEach {
                    handleVersionRestore(context, pkg, it)
                }
                querySize = data.size
                pageNumber++
            } while (querySize == pageSize)
        }
    }

    private fun handleVersionRestore(
        context: SeparationContext,
        pkg: PackageFilterInfo?,
        packageInfo: TSeparationPackage,
    ) {
        with(context) {
            context.restoreDates!!.forEach {
                var pageNumber = 0
                val pageSize = BATCH_SIZE
                var querySize: Int
                val query = SeparationQueryHelper.versionListQuery(
                    packageInfo.id!!, it, nameRegex = pkg?.versionRegex, versionList = pkg?.versions
                )
                do {
                    val pageQuery = query.with(PageRequest.of(pageNumber, pageSize))
                    val data = separationPackageVersionDao.find(pageQuery)
                    if (data.isEmpty()) {
                        logger.warn("Could not find cold version $pkg in $projectId|$repoName !")
                        break
                    }
                    data.forEach {
                        restoreColdData(context, packageInfo, it)
                    }
                    querySize = data.size
                    pageNumber++
                } while (querySize == pageSize)
            }
        }
    }


    private fun restoreColdData(
        context: SeparationContext, packageInfo: TSeparationPackage,
        packageVersionInfo: TSeparationPackageVersion,
    ) {
        with(context) {
            try {
                val versionSeparationInfo = VersionSeparationInfo(
                    projectId = projectId,
                    repoName = repoName,
                    type = repoType,
                    packageKey = packageInfo.key,
                    version = packageVersionInfo.name,
                    separationDate = packageVersionInfo.separationDate,
                    overwrite = overwrite
                )
                // 查找出版本对应节点
                val nodeRecordsMap = RepoSpecialSeparationMappings.getRestoreNodesOfVersion(versionSeparationInfo)
                if (nodeRecordsMap.isEmpty()) {
                    logger.warn(
                        "no version ${packageVersionInfo.name} of package ${packageInfo.key} " +
                            "need to be restored before $separationDate in $projectId|$repoName "
                    )
                    return
                }
                //  restore到热表, 并增加引用,
                val idSha256Map = restoreColdNodes(context, nodeRecordsMap, packageVersionInfo.separationDate)
                // copy对应依赖源特殊数据
                RepoSpecialSeparationMappings.restoreRepoColdData(versionSeparationInfo)
                // copy package以及version信息
                restorePackageVersion(context, packageInfo, packageVersionInfo)
                //删除package, 删除node,
                removeCodeDataInSeparation(
                    context, idSha256Map, packageVersionInfo.separationDate, packageInfo, packageVersionInfo
                )
                setSuccessProgress(context.separationProgress, packageInfo.id, packageVersionInfo.id)
            } catch (e: Exception) {
                logger.error(
                    "copy cold package ${packageInfo.key} with version ${packageVersionInfo.name}" +
                        " failed, error: ${e.message}"
                )
                separationFailedRecordDao.save(
                    buildTSeparationFailedRecord(
                        context, packageVersionInfo.separationDate, packageInfo.id, packageVersionInfo.id, RESTORE
                    )
                )
                setFailedProgress(context.separationProgress, packageInfo.id, packageVersionInfo.id)
            }
        }
    }


    private fun restoreColdNodes(
        context: SeparationContext, fullPaths: MutableMap<String, String>, separationDate: LocalDateTime
    ): MutableMap<String, String> {
        val idSha256Map = mutableMapOf<String, String>()
        fullPaths.forEach {
            val (id, sha256) = restoreColdNode(context, it.value, it.key, separationDate)
            idSha256Map[id] = sha256
        }
        return idSha256Map
    }

    private fun restoreColdNode(
        context: SeparationContext, fullPath: String,
        id: String, separationDate: LocalDateTime
    ): Pair<String, String> {
        with(context) {
            val nodeRecord = separationNodeDao.findById(id, separationDate)
            if (nodeRecord == null) {
                logger.error(
                    "restore node $id with " +
                        "fullPath $fullPath failed in $projectId|$repoName at $separationDate"
                )
                throw SeparationDataNotFoundException(id)
            }
            // 存储冷数据到热表中
            storeColdNodeToSource(context, nodeRecord)
            return Pair(nodeRecord.id!!, nodeRecord.sha256!!)
        }
    }

    private fun storeColdNodeToSource(context: SeparationContext, nodeRecord: TSeparationNode) {
        with(context) {
            val nodeCollectionName = SeparationUtils.getNodeCollectionName(projectId)
            val existNodeQuery = Query(
                Criteria.where(NodeDetailInfo::projectId.name).isEqualTo(projectId)
                    .and(NodeDetailInfo::repoName.name).isEqualTo(repoName)
                    .and(NodeDetailInfo::fullPath.name).isEqualTo(nodeRecord.fullPath)
                    .and(NodeDetailInfo::deleted.name).isEqualTo(null)
            )
            // 插入前判断热表中 node 是否存在，如存在根据配置是否覆盖
            val hotNode = mongoTemplate.findOne(existNodeQuery, NodeDetailInfo::class.java, nodeCollectionName)
            val storedHotNode: NodeDetailInfo
            if (hotNode != null && overwrite) {
                hotNode.apply {
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
                storedHotNode = mongoTemplate.save(hotNode, nodeCollectionName)
                logger.info("restore update hot node ${nodeRecord.fullPath} success in $projectId|$repoName")
            } else {
                val codeNode = buildNodeDetailInfo(nodeRecord)
                storedHotNode = mongoTemplate.save(codeNode, nodeCollectionName)
                logger.info("restore create hot node ${nodeRecord.fullPath} success in $projectId|$repoName")
            }
            if (!sha256Check(nodeRecord.folder, nodeRecord.sha256)) return
            increment(storedHotNode.sha256!!, credentialsKey, 1)
        }
    }

    private fun restorePackageVersion(
        context: SeparationContext,
        packageInfo: TSeparationPackage,
        packageVersionInfo: TSeparationPackageVersion
    ) {
        val packageId = restoreColdPackage(context, packageInfo)
        restoreColdPackageVersion(context, packageId, packageVersionInfo)
    }

    private fun restoreColdPackageVersion(
        context: SeparationContext, packageId: String,
        packageVersionInfo: TSeparationPackageVersion,
    ) {
        val storedColdVersion = separationPackageVersionDao.findById(
            packageVersionInfo.id!!, packageVersionInfo.separationDate
        )
        if (storedColdVersion == null) {
            logger.error(
                "restore separation version $packageVersionInfo with packageId $packageId" +
                    " failed in ${context.projectId}|${context.repoName}"
            )
            throw SeparationDataNotFoundException(packageVersionInfo.id!!)
        }
        val versionQuery = Query(
            Criteria.where(VersionDetailInfo::packageId.name).isEqualTo(packageId)
                .and(VersionDetailInfo::name.name).isEqualTo(packageVersionInfo.name)
        )
        var existHotVersionRecord = mongoTemplate.findOne(
            versionQuery,
            VersionDetailInfo::class.java, PACKAGE_VERSION_COLLECTION_NAME
        )
        storedColdVersion.packageId = packageId
        if (existHotVersionRecord != null && context.overwrite) {
            existHotVersionRecord.apply {
                lastModifiedBy = storedColdVersion.lastModifiedBy
                lastModifiedDate = storedColdVersion.lastModifiedDate
                size = storedColdVersion.size
                ordinal = storedColdVersion.ordinal
                downloads = storedColdVersion.downloads
                manifestPath = storedColdVersion.manifestPath
                artifactPath = storedColdVersion.artifactPath
                stageTag = storedColdVersion.stageTag
                metadata = storedColdVersion.metadata
                tags = storedColdVersion.tags
                extension = storedColdVersion.extension
                clusterNames = storedColdVersion.clusterNames
            }
            mongoTemplate.save(existHotVersionRecord, PACKAGE_VERSION_COLLECTION_NAME)
            logger.info(
                "restore update package version[${packageId}|${existHotVersionRecord.name}] " +
                    "success in ${context.projectId}|${context.repoName}"
            )
        } else {
            existHotVersionRecord = buildVersionDetailInfo(storedColdVersion)
            mongoTemplate.save(existHotVersionRecord, PACKAGE_VERSION_COLLECTION_NAME)
            logger.info(
                "restore package version[${packageId}|${existHotVersionRecord.name}] " +
                    "success in ${context.projectId}|${context.repoName}"
            )
        }
    }

    private fun restoreColdPackage(context: SeparationContext, packageInfo: TSeparationPackage): String {
        val storedColdPackage = separationPackageDao.findById(packageInfo.id!!)
        if (storedColdPackage == null) {
            logger.error(
                "restore separation package $packageInfo not found " +
                    "in ${context.projectId}|${context.repoName}"
            )
            throw SeparationDataNotFoundException(packageInfo.id!!)
        }
        val packageQuery = Query(
            Criteria.where(PackageDetailInfo::projectId.name).isEqualTo(context.projectId)
                .and(PackageDetailInfo::repoName.name).isEqualTo(context.repoName)
                .and(PackageDetailInfo::key.name).isEqualTo(storedColdPackage.key)
        )
        var existHotPackageRecord = mongoTemplate.findOne(
            packageQuery, PackageDetailInfo::class.java, PACKAGE_COLLECTION_NAME
        )
        if (existHotPackageRecord != null && context.overwrite) {
            existHotPackageRecord.apply {
                lastModifiedBy = storedColdPackage.lastModifiedBy
                lastModifiedDate = storedColdPackage.lastModifiedDate
                downloads = storedColdPackage.downloads
                versions = storedColdPackage.versions
                versionTag = storedColdPackage.versionTag
                extension = storedColdPackage.extension
                description = storedColdPackage.description
                historyVersion = storedColdPackage.historyVersion
                clusterNames = storedColdPackage.clusterNames
                latest = storedColdPackage.latest
            }
            mongoTemplate.save(existHotPackageRecord, PACKAGE_COLLECTION_NAME)
            logger.info(
                "restore update package[${existHotPackageRecord.id}] success" +
                    " in ${context.projectId}|${context.repoName}"
            )
        } else {
            existHotPackageRecord = buildPackageDetailInfo(storedColdPackage)
            mongoTemplate.save(existHotPackageRecord, PACKAGE_COLLECTION_NAME)
            logger.info(
                "restore package[${existHotPackageRecord.id}] success" +
                    " in ${context.projectId}|${context.repoName}"
            )
        }
        return existHotPackageRecord.id!!
    }

    private fun removeCodeDataInSeparation(
        context: SeparationContext, idSha256Map: MutableMap<String, String>, separationDate: LocalDateTime,
        packageInfo: TSeparationPackage? = null, packageVersionInfo: TSeparationPackageVersion? = null,
    ) {
        if (packageInfo != null && packageVersionInfo != null) {
            removeColdVersionFromSeparation(context, packageInfo, packageVersionInfo)
        }
        // 删除节点
        removeColdNodeFromSeparation(context, idSha256Map, separationDate)
    }

    private fun removeColdVersionFromSeparation(
        context: SeparationContext, packageInfo: TSeparationPackage,
        packageVersionInfo: TSeparationPackageVersion
    ) {
        separationPackageDao.removeById(packageInfo.id!!)
        logger.info(
            "delete stored code version $packageVersionInfo success " +
                "for $packageInfo in ${context.projectId}|${context.repoName}"
        )
    }

    private fun removeColdNodeFromSeparation(
        context: SeparationContext,
        idSha256Map: MutableMap<String, String>,
        separationDate: LocalDateTime
    ) {
        with(context) {
            idSha256Map.forEach {
                // 删除， 同时删除索引
                val updateResult = separationNodeDao.removeById(it.key, separationDate)
                if (updateResult.deletedCount == 0L) {
                    logger.error("delete separation node ${it.value} failed in $projectId|$repoName")
                    return
                }
                logger.error("delete separation node ${it.value} success in $projectId|$repoName")
                if (!sha256Check(false, it.value)) {
                    logger.warn("separation node[${it.key}] sha256 is null or blank.")
                    return
                }
                increment(it.value, credentialsKey, -1)
            }
        }
    }

    private fun handleNodeRestore(context: SeparationContext, node: NodeFilterInfo? = null) {
        with(context) {
            validateNodeParams(node)
            if (context.restoreDates.isNullOrEmpty())
                throw NotFoundException(
                    CommonMessageCode.REQUEST_CONTENT_INVALID,
                    "no separation task for $projectId|$repoName"
                )
            context.restoreDates.forEach {
                var pageNumber = 0
                val pageSize = BATCH_SIZE
                var querySize = 0
                val query = SeparationQueryHelper.pathQuery(projectId, repoName, it, node?.path, node?.pathRegex)
                do {
                    val nodeQuery = query.with(PageRequest.of(pageNumber, pageSize))
                    val data = separationNodeDao.find(nodeQuery)
                    if (data.isEmpty()) {
                        logger.warn("Could not find cold node $node in $projectId|$repoName !")
                        break
                    }
                    data.forEach { cNode ->
                        try {
                            val (id, sha256) = restoreColdNode(context, cNode.fullPath, cNode.id!!, it)
                            // 逻辑删除node,
                            removeCodeDataInSeparation(context, mutableMapOf(id to sha256), it)
                            setSuccessProgress(context.separationProgress, nodeId = cNode.id)
                        } catch (e: Exception) {
                            logger.error(
                                "restore cold node ${cNode.id} with path ${cNode.fullPath} at $it" +
                                    " failed, error: ${e.message}"
                            )
                            separationFailedRecordDao.save(buildTSeparationFailedRecord(context, it, nodeId = cNode.id))
                            setFailedProgress(context.separationProgress, nodeId = cNode.id)
                        }
                    }
                    querySize = data.size
                    pageNumber++
                } while (querySize == pageSize)
            }
        }
    }

    private fun buildNodeDetailInfo(node: TSeparationNode): NodeDetailInfo {
        return NodeDetailInfo(
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
        )
    }

    private fun buildVersionDetailInfo(
        versionDetail: TSeparationPackageVersion
    ): VersionDetailInfo {
        return VersionDetailInfo(
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
        )
    }

    private fun buildPackageDetailInfo(
        packageDetail: TSeparationPackage
    ): PackageDetailInfo {
        return PackageDetailInfo(
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
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataRestorerImpl::class.java)
    }
}