/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.service.packages.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.dao.packages.PackageVersionDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.search.packages.PackageSearchInterpreter
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.common.metadata.util.PackageEventFactory
import com.tencent.bkrepo.common.metadata.util.PackageEventFactory.buildCreatedEvent
import com.tencent.bkrepo.common.metadata.util.PackageEventFactory.buildUpdatedEvent
import com.tencent.bkrepo.common.metadata.util.PackageQueryHelper
import com.tencent.bkrepo.common.metadata.util.version.SemVersion
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.repository.constant.ORDINAL
import com.tencent.bkrepo.repository.constant.PACKAGE_KEY_SEPARATOR
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.pojo.packages.request.PackageCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackagePopulateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionUpdateRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(SyncCondition::class)
class PackageServiceImpl(
    repositoryDao: RepositoryDao,
    packageDao: PackageDao,
    protected val packageVersionDao: PackageVersionDao,
    private val packageSearchInterpreter: PackageSearchInterpreter
) : PackageBaseService(repositoryDao, packageDao) {

    override fun findPackageByKey(projectId: String, repoName: String, packageKey: String): PackageSummary? {
        val tPackage = packageDao.findByKey(projectId, repoName, packageKey)
        return convert(tPackage)
    }

    override fun findVersionByName(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String
    ): PackageVersion? {
        val packageId = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey)?.id ?: return null
        return convert(packageVersionDao.findByName(packageId, versionName))
    }

    override fun findVersionNameByTag(
        projectId: String,
        repoName: String,
        packageKey: String,
        tag: String
    ): String? {
        val versionTag = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey)?.versionTag
            ?: return null
        return versionTag[tag]
    }

    override fun findLatestBySemVer(
        projectId: String,
        repoName: String,
        packageKey: String
    ): PackageVersion? {
        val packageId = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey)?.id ?: return null
        return convert(packageVersionDao.findLatest(packageId))
    }

    override fun listPackagePage(
        projectId: String,
        repoName: String,
        option: PackageListOption
    ): Page<PackageSummary> {
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
        Preconditions.checkArgument(pageSize >= 0, "pageSize")
        val query = PackageQueryHelper.packageListQuery(projectId, repoName, option.packageName)
        val totalRecords = packageDao.count(query)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val records = packageDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun listAllPackageName(projectId: String, repoName: String): List<String> {
        val query = PackageQueryHelper.packageListQuery(projectId, repoName, null)
        query.fields().include(TPackage::key.name)
        return packageDao.find(query, Map::class.java).map {
            it[TPackage::key.name].toString()
        }
    }

    override fun listVersionPage(
        projectId: String,
        repoName: String,
        packageKey: String,
        option: VersionListOption
    ): Page<PackageVersion> {
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
        Preconditions.checkArgument(pageSize >= 0, "pageSize")
        val stageTag = option.stageTag?.split(StringPool.COMMA)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val tPackage = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey)
        return if (tPackage == null) {
            Pages.ofResponse(pageRequest, 0, emptyList())
        } else {
            val query = PackageQueryHelper.versionListQuery(
                packageId = tPackage.id!!,
                name = option.version,
                stageTag = stageTag,
                sortProperty = option.sortProperty ?: determineVersionSortProperty(packageKey),
                direction = Sort.Direction.fromOptionalString(option.direction.toString()).orElse(Sort.Direction.DESC)
            )
            val totalRecords = packageVersionDao.count(query)
            val records = packageVersionDao.find(query.with(pageRequest)).map { convert(it)!! }
            Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }

    override fun listAllVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        option: VersionListOption
    ): List<PackageVersion> {
        val stageTag = option.stageTag?.split(StringPool.COMMA)
        val tPackage = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey) ?: return emptyList()
        val query = PackageQueryHelper.versionListQuery(
            packageId = tPackage.id!!,
            name = option.version,
            stageTag = stageTag,
            metadata = option.metadata,
            sortProperty = option.sortProperty ?: determineVersionSortProperty(packageKey),
            direction = Sort.Direction.fromOptionalString(option.direction.toString()).orElse(Sort.Direction.DESC)
        )
        return packageVersionDao.find(query).map { convert(it)!! }
    }

    override fun createPackageVersion(request: PackageVersionCreateRequest, realIpAddress: String?) {
        with(request) {
            Preconditions.checkNotBlank(packageKey, this::packageKey.name)
            Preconditions.checkNotBlank(packageName, this::packageName.name)
            Preconditions.checkNotBlank(versionName, this::versionName.name)
            // 先查询包是否存在，不存在先创建包
            val tPackage = findOrCreatePackage(buildPackage(request))
            // 检查版本是否存在
            var oldVersion = packageVersionDao.findByName(tPackage.id!!, versionName)
            val query = Query(
                where(TPackage::projectId).isEqualTo(projectId).apply {
                    and(TPackage::repoName).isEqualTo(repoName)
                    and(TPackage::key).isEqualTo(packageKey)
                }
            )
            val latest = if (determineVersionSortProperty(tPackage.key) == ORDINAL) {
                val currentLatest = tPackage.latest
                if (currentLatest == null || SemVersion.parse(versionName) > SemVersion.parse(currentLatest)) {
                    versionName
                } else null
            } else versionName
            val update = Update().set(TPackage::lastModifiedBy.name, request.createdBy)
                .set(TPackage::lastModifiedDate.name, LocalDateTime.now())
                .set(TPackage::description.name, packageDescription)
                .set(TPackage::extension.name, packageExtension)
                .set(TPackage::versionTag.name, mergeVersionTag(tPackage.versionTag, versionTag))
                .set(TPackage::historyVersion.name, tPackage.historyVersion.toMutableSet().apply { add(versionName) })
                .apply { if (latest != null) set(TPackage::latest.name, latest) }
            // 检查本次上传是创建还是覆盖。
            if (oldVersion != null) {
                updateExistVersion(
                    oldVersion = oldVersion,
                    request = request,
                    realIpAddress = realIpAddress,
                    packageQuery = query,
                    packageUpdate = update
                )
            } else {
                createVersion(
                    request = request,
                    query = query,
                    update = update,
                    tPackage = tPackage,
                    realIpAddress = realIpAddress
                )
            }
            populateCluster(tPackage)
        }
    }

    private fun createVersion(
        request: PackageVersionCreateRequest,
        realIpAddress: String?,
        update: Update,
        query: Query,
        tPackage: TPackage,
    ) {
        // create new
        val newVersion = buildPackageVersion(request, tPackage.id!!)
        try {
            packageVersionDao.save(newVersion)
            // 改为通过mongo的原子操作来更新。微服务持有版本数在并发下会导致版本数被覆盖的问题
            update.inc(TPackage::versions.name)
            packageDao.upsert(query, update)
            logger.info("Create package version[$newVersion] success")
            publishEvent(buildCreatedEvent(request, realIpAddress ?: HttpContextHolder.getClientAddress()))
        } catch (exception: DuplicateKeyException) {
            logger.warn("Create version[$newVersion] error: [${exception.message}]")
            val oldVersion = packageVersionDao.findByName(tPackage.id!!, request.versionName)
            updateExistVersion(
                oldVersion = oldVersion!!,
                request = request,
                realIpAddress = realIpAddress,
                packageQuery = query,
                packageUpdate = update
            )
        }
    }

    override fun createPackage(request: PackageCreateRequest, realIpAddress: String?) {
        with(request) {
            Preconditions.checkNotBlank(packageKey, this::packageKey.name)
            Preconditions.checkNotBlank(packageName, this::packageName.name)
            // 先查询包是否存在，不存在先创建包
            findOrCreatePackage(buildPackage(request))
        }
    }

    override fun deletePackage(projectId: String, repoName: String, packageKey: String, realIpAddress: String?) {
        val tPackage = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey) ?: return
        packageVersionDao.deleteByPackageId(tPackage.id!!)
        packageDao.deleteByKey(projectId, repoName, packageKey)
        publishEvent(
            PackageEventFactory.buildDeletedEvent(
                projectId = projectId,
                repoName = repoName,
                packageType = tPackage.type,
                packageKey = packageKey,
                packageName = tPackage.name,
                versionName = null,
                createdBy = SecurityUtils.getUserId(),
                realIpAddress = realIpAddress ?: HttpContextHolder.getClientAddress()
            )
        )
        logger.info("Delete package [$projectId/$repoName/$packageKey] success")
    }

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String,
        realIpAddress: String?,
        contentPath: String?,
    ) {
        var tPackage = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey) ?: return
        val packageId = tPackage.id!!
        val tPackageVersion = packageVersionDao.findByName(packageId, versionName) ?: return
        checkCluster(tPackageVersion)
        val deleted = packageVersionDao.deleteByNameAndPath(packageId, tPackageVersion.name, contentPath)
        if (deleted) {
            tPackage = packageDao.decreaseVersions(packageId) ?: return
        }
        if (tPackage.versions <= 0L) {
            packageDao.removeById(packageId)
            logger.info("Delete package [$projectId/$repoName/$packageKey-$versionName] because no version exist")
        } else {
            if (deleted && tPackage.latest == tPackageVersion.name) {
                val latestVersion = packageVersionDao.findLatest(packageId)
                packageDao.updateLatestVersion(packageId, latestVersion?.name.orEmpty())
            }
        }
        if (deleted) {
            publishEvent(
                PackageEventFactory.buildDeletedEvent(
                    projectId = projectId,
                    repoName = repoName,
                    packageType = tPackage.type,
                    packageKey = packageKey,
                    packageName = tPackage.name,
                    versionName = versionName,
                    createdBy = SecurityUtils.getUserId(),
                    realIpAddress = realIpAddress ?: HttpContextHolder.getClientAddress()
                )
            )
        }
        logger.info("Delete package version[$projectId/$repoName/$packageKey-$versionName] success")
    }

    override fun deleteAllPackage(projectId: String, repoName: String) {
        val query = PackageQueryHelper.packageListQuery(projectId, repoName, null)
        var pageNumber = 1
        do {
            val packageList = packageDao.find(query.with(Pages.ofRequest(pageNumber, 1000)))
            packageList.forEach { deletePackage(it.projectId, it.repoName, it.key) }
            pageNumber++
        } while (packageList.isNotEmpty())
    }

    override fun updatePackage(request: PackageUpdateRequest, realIpAddress: String?) {
        val projectId = request.projectId
        val repoName = request.repoName
        val packageKey = request.packageKey
        val tPackage = checkPackage(projectId, repoName, packageKey).apply {
            checkCluster(this)
            name = request.name ?: name
            key = request.key ?: packageKey
            description = request.description ?: description
            versionTag = request.versionTag ?: versionTag
            extension = request.extension ?: extension
            lastModifiedBy = SecurityUtils.getUserId()
            lastModifiedDate = LocalDateTime.now()
        }
        packageDao.save(tPackage)
    }

    override fun updateVersion(request: PackageVersionUpdateRequest, realIpAddress: String?) {
        val operator = SecurityUtils.getUserId()
        val projectId = request.projectId
        val repoName = request.repoName
        val packageKey = request.packageKey
        val versionName = request.versionName
        val newMetadata = if (request.metadata != null || request.packageMetadata != null) {
            MetadataUtils.compatibleConvertAndCheck(request.metadata, request.packageMetadata)
        } else {
            null
        }
        val tPackage = findPackageExcludeHistoryVersion(projectId, repoName, packageKey)
        val packageId = tPackage.id.orEmpty()
        val tPackageVersion = checkPackageVersion(packageId, versionName).apply {
            checkCluster(this)
            size = request.size ?: size
            manifestPath = request.manifestPath ?: manifestPath
            artifactPath = request.artifactPath ?: artifactPath
            stageTag = request.stageTag ?: stageTag
            metadata = newMetadata ?: metadata
            tags = request.tags ?: tags
            extension = request.extension ?: extension
            lastModifiedBy = operator
            lastModifiedDate = LocalDateTime.now()
        }
        packageVersionDao.save(tPackageVersion)
        publishEvent(
            buildUpdatedEvent(
                request = request,
                packageType = tPackage.type.name,
                packageName = tPackage.name,
                createdBy = SecurityUtils.getUserId(),
                realIpAddress = realIpAddress ?: HttpContextHolder.getClientAddress()
            )
        )
    }

    override fun addDownloadRecord(projectId: String, repoName: String, packageKey: String, versionName: String) {
        val tPackage = checkPackage(projectId, repoName, packageKey)
        val tPackageVersion = checkPackageVersion(tPackage.id!!, versionName)
        tPackageVersion.downloads += 1
        packageVersionDao.save(tPackageVersion)
        tPackage.downloads += 1
        packageDao.save(tPackage)
    }

    override fun searchPackage(queryModel: QueryModel): Page<MutableMap<*, *>> {
        val context = packageSearchInterpreter.interpret(queryModel)
        val query = context.mongoQuery
        val countQuery = Query.of(query).limit(0).skip(0)
        val totalRecords = packageDao.count(countQuery)
        val packageList = packageDao.find(query, MutableMap::class.java)
        val pageNumber = if (query.limit == 0) 0 else (query.skip / query.limit).toInt()
        return Page(pageNumber + 1, query.limit, totalRecords, packageList)
    }

    override fun listExistPackageVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        packageVersionList: List<String>
    ): List<String> {
        val tPackage = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey) ?: return emptyList()
        val versionQuery = PackageQueryHelper.versionQuery(tPackage.id!!, packageVersionList)
        return packageVersionDao.find(versionQuery).map { it.name }
    }

    override fun populatePackage(request: PackagePopulateRequest) {
        with(request) {
            // 先查询包是否存在，不存在先创建包
            val tPackage = findOrCreatePackage(buildPackage(request))
            val packageId = tPackage.id.orEmpty()
            var latestVersion = packageVersionDao.findLatest(packageId)
            // 检查版本是否存在
            versionList.forEach {
                if (packageVersionDao.findByName(packageId, it.name) != null) {
                    logger.info("Package version[${tPackage.name}-${it.name}] existed, skip populating.")
                    return@forEach
                }
                val newVersion = buildPackageVersion(it, packageId)
                packageVersionDao.save(newVersion)
                tPackage.versions += 1
                tPackage.downloads += it.downloads
                tPackage.versionTag = mergeVersionTag(tPackage.versionTag, versionTag)
                if (latestVersion == null) {
                    latestVersion = newVersion
                } else if (it.createdDate.isAfter(latestVersion?.createdDate)) {
                    latestVersion = newVersion
                }
                logger.info("Create package version[$newVersion] success")
            }
            // 更新包
            tPackage.latest = latestVersion?.name ?: tPackage.latest
            packageDao.save(tPackage)
            populateCluster(tPackage)
            logger.info("Update package version[$tPackage] success")
        }
    }

    override fun getPackageCount(projectId: String, repoName: String): Long {
        val query = PackageQueryHelper.packageListQuery(projectId, repoName, null)
        return packageDao.count(query)
    }

    /**
     * 更新已经存在的版本信息
     */
    private fun updateExistVersion(
        oldVersion: TPackageVersion,
        request: PackageVersionCreateRequest,
        realIpAddress: String?,
        packageQuery: Query,
        packageUpdate: Update
    ) {
        with(request) {
            checkPackageVersionOverwrite(overwrite, packageName, oldVersion)
            // overwrite
            oldVersion.apply {
                lastModifiedBy = request.createdBy
                lastModifiedDate = LocalDateTime.now()
                size = request.size
                manifestPath = request.manifestPath
                artifactPath = request.artifactPath
                artifactPaths = buildArtifactPaths(request)
                stageTag = request.stageTag.orEmpty()
                metadata = MetadataUtils.compatibleConvertAndCheck(request.metadata, packageMetadata)
                tags = request.tags?.filter { it.isNotBlank() }.orEmpty()
                extension = request.extension.orEmpty()
            }
            packageVersionDao.save(oldVersion)
            packageDao.upsert(packageQuery, packageUpdate)
            logger.info("Update package version[$oldVersion] success")
            publishEvent(buildUpdatedEvent(request, realIpAddress ?: HttpContextHolder.getClientAddress()))
        }
    }

    private fun TPackageVersion.buildArtifactPaths(request: PackageVersionCreateRequest): MutableSet<String>? {
        request.artifactPath?.let {
             return if (!request.multiArtifact) {
                mutableSetOf(it)
            } else {
                artifactPaths?.add(it)
                artifactPaths ?: mutableSetOf(it)
            }
        }
        return null
    }

    /**
     * 查找包，不存在则抛异常
     */
    private fun checkPackage(projectId: String, repoName: String, packageKey: String): TPackage {
        return packageDao.findByKey(projectId, repoName, packageKey)
            ?: throw ErrorCodeException(ArtifactMessageCode.PACKAGE_NOT_FOUND, packageKey)
    }

    /**
     * 查找包，不存在则抛异常
     */
    private fun findPackageExcludeHistoryVersion(projectId: String, repoName: String, packageKey: String): TPackage {
        return packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey)
            ?: throw ErrorCodeException(ArtifactMessageCode.PACKAGE_NOT_FOUND, packageKey)
    }


    /**
     * 查找版本，不存在则抛异常
     */
    private fun checkPackageVersion(packageId: String, versionName: String): TPackageVersion {
        return packageVersionDao.findByName(packageId, versionName)
            ?: throw ErrorCodeException(ArtifactMessageCode.VERSION_NOT_FOUND, versionName)
    }

    private fun determineVersionSortProperty(packageKey: String): String {
        return PackageType.fromSchema(packageKey.substringBefore(PACKAGE_KEY_SEPARATOR))?.versionSortProperty ?: ORDINAL
    }

    companion object {

        private val logger = LoggerFactory.getLogger(PackageServiceImpl::class.java)

        private fun convert(tPackage: TPackage?): PackageSummary? {
            return tPackage?.let {
                PackageSummary(
                    id = it.id!!,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    projectId = it.projectId,
                    repoName = it.repoName,
                    name = it.name,
                    key = it.key,
                    type = it.type,
                    latest = it.latest.orEmpty(),
                    downloads = it.downloads,
                    versions = it.versions,
                    description = it.description,
                    versionTag = it.versionTag.orEmpty(),
                    extension = it.extension.orEmpty(),
                    historyVersion = it.historyVersion
                )
            }
        }

        private fun convert(tPackageVersion: TPackageVersion?): PackageVersion? {
            return tPackageVersion?.let {
                PackageVersion(
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    name = it.name,
                    size = it.size,
                    downloads = it.downloads,
                    stageTag = it.stageTag,
                    metadata = MetadataUtils.toMap(it.metadata),
                    packageMetadata = MetadataUtils.toList(it.metadata),
                    tags = it.tags.orEmpty(),
                    extension = it.extension.orEmpty(),
                    contentPath = it.artifactPath,
                    contentPaths = it.artifactPaths ?: it.artifactPath?.let { path -> setOf(path) },
                    manifestPath = it.manifestPath,
                    clusterNames = it.clusterNames
                )
            }
        }
    }
}
