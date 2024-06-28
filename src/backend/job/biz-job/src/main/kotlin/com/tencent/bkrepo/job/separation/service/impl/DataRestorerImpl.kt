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

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.PACKAGE_COLLECTION_NAME
import com.tencent.bkrepo.job.PACKAGE_VERSION_COLLECTION_NAME
import com.tencent.bkrepo.job.separation.dao.SeparationFailedRecordDao
import com.tencent.bkrepo.job.separation.dao.SeparationNodeDao
import com.tencent.bkrepo.job.separation.dao.SeparationPackageDao
import com.tencent.bkrepo.job.separation.dao.SeparationPackageVersionDao
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
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
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class DataRestorerImpl(
    private val mongoTemplate: MongoTemplate,
    private val separationPackageVersionDao: SeparationPackageVersionDao,
    private val separationPackageDao: SeparationPackageDao,
    separationFailedRecordDao: SeparationFailedRecordDao,
    private val separationNodeDao: SeparationNodeDao,
    separationTaskDao: SeparationTaskDao,
) : AbstractHandler(mongoTemplate, separationFailedRecordDao, separationTaskDao), DataRestorer {
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
            packageKey = version.packageKey,
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
        with(context) {
            validatePackageParams(pkg)
            var pageNumber = 0
            val pageSize = BATCH_SIZE
            var querySize: Int
            val query = SeparationQueryHelper.packageKeyQuery(
                projectId, repoName, pkg?.packageKey, pkg?.packageKeyRegex
            )

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
            var pageNumber = 0
            val pageSize = BATCH_SIZE
            var querySize: Int
            val query = SeparationQueryHelper.versionListQuery(
                packageInfo.id!!, separationDate, nameRegex = pkg?.versionRegex, versionList = pkg?.versions
            )
            do {
                val pageQuery = query.with(PageRequest.of(pageNumber, pageSize))
                val data = separationPackageVersionDao.find(pageQuery)
                if (data.isEmpty()) {
                    logger.warn("Could not find cold version $pkg before $separationDate in $projectId|$repoName !")
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


    private fun restoreColdData(
        context: SeparationContext, packageInfo: TSeparationPackage,
        packageVersionInfo: TSeparationPackageVersion,
    ) {
        with(context) {
            try {
                val versionSeparationInfo = buildVersionSeparationInfo(
                    context, packageInfo.key, packageVersionInfo.name
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
                val idSha256Map = restoreColdNodes(context, nodeRecordsMap)
                // copy对应依赖源特殊数据
                RepoSpecialSeparationMappings.restoreRepoColdData(versionSeparationInfo)
                // copy package以及version信息
                restorePackageVersion(context, packageInfo, packageVersionInfo)
                //删除package, 删除node,
                removeCodeDataInSeparation(
                    context, idSha256Map, packageInfo, packageVersionInfo
                )
                setSuccessProgress(
                    context.taskId, context.separationProgress, packageInfo.id, packageVersionInfo.id
                )
                removeFailedRecord(
                    context, packageInfo.id, packageVersionInfo.id
                )
            } catch (e: Exception) {
                logger.error(
                    "restore cold package ${packageInfo.key} with version ${packageVersionInfo.name}" +
                        " failed, error: ${e.message}"
                )
                saveFailedRecord(
                    context, packageInfo.id, packageVersionInfo.id
                )
                setFailedProgress(
                    context.taskId, context.separationProgress, packageInfo.id, packageVersionInfo.id
                )
            }
        }
    }


    private fun buildVersionSeparationInfo(
        context: SeparationContext, packageKey: String,
        version: String
    ): VersionSeparationInfo {
        with(context) {
            return VersionSeparationInfo(
                projectId = projectId,
                repoName = repoName,
                type = repoType,
                packageKey = packageKey,
                version = version,
                separationDate = separationDate,
                overwrite = overwrite
            )
        }
    }

    private fun restoreColdNodes(
        context: SeparationContext, fullPaths: MutableMap<String, String>
    ): MutableMap<String, String> {
        val idSha256Map = mutableMapOf<String, String>()
        fullPaths.forEach {
            val (id, sha256) = restoreColdNode(context, it.value, it.key)
            idSha256Map[id] = sha256
        }
        return idSha256Map
    }

    private fun restoreColdNode(
        context: SeparationContext, fullPath: String, id: String
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
            // 如果存在而且不需要覆盖，则直接返回
            if (hotNode != null && !overwrite) return
            try {
                if (hotNode != null && overwrite) {
                    // 存在则先删除，再新增，这样出问题也可以恢复删除节点
                    removeExistNode(hotNode, nodeCollectionName)
                }
                val codeNode = buildNodeDetailInfo(nodeRecord)
                mongoTemplate.save(codeNode, nodeCollectionName)
                logger.info(
                    "restore create hot node ${nodeRecord.id}" +
                        " with ${nodeRecord.fullPath} success in $projectId|$repoName"
                )
                if (!sha256Check(nodeRecord.folder, nodeRecord.sha256)) return
            } catch (e: DuplicateKeyException) {
                logger.warn(
                    "restore hot node ${nodeRecord.id} with fullPath ${nodeRecord.fullPath} occurred" +
                        " DuplicateKeyException in $projectId|$repoName"
                )
                return
            }
            // 只有新增的时候才去尽显文件索引新增
            increment(nodeRecord.sha256!!, credentialsKey, 1)
        }
    }

    private fun removeExistNode(
        hotNode: NodeDetailInfo,
        nodeCollectionName: String
    ) {
        val nodeQuery = Query(Criteria.where(ID).isEqualTo(hotNode.id))
        // 逻辑删除， 同时删除索引
        val update = Update()
            .set(NodeDetailInfo::lastModifiedBy.name, SYSTEM_USER)
            .set(NodeDetailInfo::deleted.name, LocalDateTime.now())
        val updateResult = mongoTemplate.updateFirst(nodeQuery, update, nodeCollectionName)
        if (updateResult.modifiedCount != 1L) {
            logger.error(
                "delete exist hot node failed with id ${hotNode.id} " +
                    "and fullPath ${hotNode.fullPath} in ${hotNode.projectId}|${hotNode.repoName}"
            )
        } else {
            logger.info(
                "delete exist node success with id ${hotNode.id} " +
                    "and fullPath ${hotNode.fullPath} in ${hotNode.projectId}|${hotNode.repoName}"
            )
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
                "restore hot version $packageVersionInfo with packageId $packageId" +
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

        // 存在且不覆盖直接返回
        if (existHotVersionRecord != null && !context.overwrite) {
            logger.info(
                "restore package version[${packageId}|${existHotVersionRecord.name}] exist " +
                    "in ${context.projectId}|${context.repoName}"
            )
            return
        }
        try {
            if (existHotVersionRecord != null && context.overwrite) {
                val update = buildPackageVersionUpdate(storedColdVersion)
                mongoTemplate.updateFirst(versionQuery, update, PACKAGE_VERSION_COLLECTION_NAME)
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
        } catch (e: DuplicateKeyException) {
            logger.warn(
                "restore hot version $storedColdVersion occurred DuplicateKeyException " +
                    "in ${context.projectId}|${context.repoName}"
            )
        }
    }

    private fun buildPackageVersionUpdate(
        storedColdVersion: TSeparationPackageVersion,
    ): Update {
        return Update().set(VersionDetailInfo::lastModifiedBy.name, storedColdVersion.lastModifiedBy)
            .set(VersionDetailInfo::lastModifiedDate.name, storedColdVersion.lastModifiedDate)
            .set(VersionDetailInfo::size.name, storedColdVersion.size)
            .set(VersionDetailInfo::ordinal.name, storedColdVersion.ordinal)
            .set(VersionDetailInfo::downloads.name, storedColdVersion.downloads)
            .set(VersionDetailInfo::extension.name, storedColdVersion.extension)
            .set(VersionDetailInfo::manifestPath.name, storedColdVersion.manifestPath)
            .set(VersionDetailInfo::artifactPath.name, storedColdVersion.artifactPath)
            .set(VersionDetailInfo::clusterNames.name, storedColdVersion.clusterNames)
            .set(VersionDetailInfo::stageTag.name, storedColdVersion.stageTag)
            .set(VersionDetailInfo::metadata.name, storedColdVersion.metadata)
            .set(VersionDetailInfo::tags.name, storedColdVersion.tags)
            .set(VersionDetailInfo::clusterNames.name, storedColdVersion.clusterNames)
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
        // 存在且不覆盖直接返回
        if (existHotPackageRecord != null && !context.overwrite) {
            logger.info(
                "restore package[${existHotPackageRecord.id}|${existHotPackageRecord.key}] exist" +
                    " in ${context.projectId}|${context.repoName}"
            )
            return existHotPackageRecord.id!!
        }
        try {
            if (existHotPackageRecord != null && context.overwrite) {
                val update = buildPackageUpdate(storedColdPackage)
                mongoTemplate.updateFirst(packageQuery, update, PACKAGE_COLLECTION_NAME)
                logger.info(
                    "restore update package[${existHotPackageRecord.id}|${existHotPackageRecord.key}] success" +
                        " in ${context.projectId}|${context.repoName}"
                )
            } else {
                existHotPackageRecord = buildPackageDetailInfo(storedColdPackage)
                mongoTemplate.save(existHotPackageRecord, PACKAGE_COLLECTION_NAME)
                logger.info(
                    "restore package[${existHotPackageRecord.id}|${existHotPackageRecord.key}] success" +
                        " in ${context.projectId}|${context.repoName}"
                )
            }
        } catch (e: DuplicateKeyException) {
            logger.warn(
                "restore hot package $existHotPackageRecord occurred DuplicateKeyException " +
                    "in ${context.projectId}|${context.repoName}"
            )
            existHotPackageRecord = mongoTemplate.findOne(
                packageQuery, PackageDetailInfo::class.java, PACKAGE_COLLECTION_NAME
            )
        }
        return existHotPackageRecord.id!!
    }

    private fun buildPackageUpdate(
        storedColdPackage: TSeparationPackage,
    ): Update {
        return Update().set(PackageDetailInfo::lastModifiedBy.name, storedColdPackage.lastModifiedBy)
            .set(PackageDetailInfo::lastModifiedDate.name, storedColdPackage.lastModifiedDate)
            .set(PackageDetailInfo::downloads.name, storedColdPackage.downloads)
            .set(PackageDetailInfo::versions.name, storedColdPackage.versions)
            .set(PackageDetailInfo::versionTag.name, storedColdPackage.versionTag)
            .set(PackageDetailInfo::extension.name, storedColdPackage.extension)
            .set(PackageDetailInfo::description.name, storedColdPackage.description)
            .set(PackageDetailInfo::historyVersion.name, storedColdPackage.historyVersion)
            .set(PackageDetailInfo::clusterNames.name, storedColdPackage.clusterNames)
            .set(PackageDetailInfo::latest.name, storedColdPackage.latest)
    }

    private fun removeCodeDataInSeparation(
        context: SeparationContext, idSha256Map: MutableMap<String, String>,
        packageInfo: TSeparationPackage? = null, packageVersionInfo: TSeparationPackageVersion? = null,
    ) {
        // 删除节点
        removeColdNodeFromSeparation(context, idSha256Map)

        if (packageInfo != null && packageVersionInfo != null) {
            removeColdVersionFromSeparation(context, packageInfo, packageVersionInfo)
            val versionSeparationInfo = buildVersionSeparationInfo(
                context, packageInfo.key, packageVersionInfo.name
            )
            RepoSpecialSeparationMappings.removeRestoredRepoColdData(versionSeparationInfo)
        }
    }

    private fun removeColdVersionFromSeparation(
        context: SeparationContext, packageInfo: TSeparationPackage,
        packageVersionInfo: TSeparationPackageVersion
    ) {
        val deletedResult = separationPackageVersionDao.removeById(
            packageVersionInfo.id!!, packageVersionInfo.separationDate
        )
        if (deletedResult.deletedCount != 1L) {
            logger.error(
                "delete restored version $packageVersionInfo failed " +
                    "for $packageInfo in ${context.projectId}|${context.repoName}"
            )
        } else {
            logger.info(
                "delete restored version $packageVersionInfo success " +
                    "for $packageInfo in ${context.projectId}|${context.repoName}"
            )
        }
    }

    private fun removeColdNodeFromSeparation(
        context: SeparationContext,
        idSha256Map: MutableMap<String, String>,
    ) {
        with(context) {
            idSha256Map.forEach {
                // 删除， 同时删除索引
                val updateResult = separationNodeDao.removeById(it.key, separationDate)
                if (updateResult.deletedCount == 0L) {
                    logger.error(
                        "delete restored node ${it.value} before $separationDate " +
                            "failed in $projectId|$repoName"
                    )
                    return
                }
                logger.info("delete restored node ${it.value} before $separationDate success in $projectId|$repoName")
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
            var pageNumber = 0
            val pageSize = BATCH_SIZE
            var querySize: Int
            val query = SeparationQueryHelper.pathQuery(
                projectId, repoName, separationDate, node?.path, node?.pathRegex
            )
            do {
                val nodeQuery = query.with(PageRequest.of(pageNumber, pageSize))
                val data = separationNodeDao.find(nodeQuery)
                if (data.isEmpty()) {
                    logger.warn("Could not find cold node $node before $separationDate in $projectId|$repoName !")
                    break
                }
                data.forEach { cNode ->
                    restoreNode(context, cNode)
                }
                querySize = data.size
                pageNumber++
            } while (querySize == pageSize)
        }
    }

    private fun restoreNode(context: SeparationContext, cNode: TSeparationNode) {
        try {
            val (id, sha256) = restoreColdNode(context, cNode.fullPath, cNode.id!!)
            // 逻辑删除node,
            removeCodeDataInSeparation(context, mutableMapOf(id to sha256))
            setSuccessProgress(context.taskId, context.separationProgress, nodeId = cNode.id)
            removeFailedRecord(
                context, nodeId = cNode.id,
            )
        } catch (e: Exception) {
            logger.error(
                "restore cold node ${cNode.id} with path ${cNode.fullPath} " +
                    "at ${context.separationDate} failed, error: ${e.message}"
            )
            saveFailedRecord(context, nodeId = cNode.id)
            setFailedProgress(context.taskId, context.separationProgress, nodeId = cNode.id)
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