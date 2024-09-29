/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.service.packages.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.util.version.SemVersion
import com.tencent.bkrepo.repository.dao.PackageDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.repository.model.ClusterResource
import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.repository.pojo.packages.request.PackagePopulateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PopulatedPackageVersion
import com.tencent.bkrepo.repository.service.packages.PackageService
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDateTime

abstract class PackageBaseService(
    protected val repositoryDao: RepositoryDao,
    protected val packageDao: PackageDao
) : PackageService {

    protected open fun checkPackageVersionOverwrite(
        overwrite: Boolean,
        packageName: String,
        oldVersion: TPackageVersion
    ) {
        if (!overwrite) {
            throw ErrorCodeException(ArtifactMessageCode.VERSION_EXISTED, packageName, oldVersion.name)
        }
    }

    /**
     * 设置Package的cluster
     */
    protected open fun populateCluster(tPackage: TPackage) {
        // do nothing
    }

    /**
     * 检查当前请求来源节点是否允许操作资源
     */
    protected open fun checkCluster(clusterResource: ClusterResource) {
        // do nothing
    }

    /**
     * 查找包，不存在则创建
     */
    protected open fun findOrCreatePackage(tPackage: TPackage): TPackage {
        with(tPackage) {
            packageDao.findByKey(projectId, repoName, key)?.let { return it }
            return try {
                packageDao.save(tPackage)
                logger.info("Create package[$tPackage] success")
                tPackage
            } catch (exception: DuplicateKeyException) {
                logger.warn("Create package[$tPackage] error: [${exception.message}]")
                packageDao.findByKey(projectId, repoName, key)!!
            }
        }
    }

    protected open fun buildPackage(request: PackageVersionCreateRequest) = with(request) {
        TPackage(
            createdBy = createdBy,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = createdBy,
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
            name = packageName.trim(),
            key = packageKey.trim(),
            type = packageType,
            downloads = 0,
            versions = 0,
            versionTag = versionTag.orEmpty(),
            extension = packageExtension.orEmpty(),
            description = packageDescription,
            historyVersion = mutableSetOf(versionName),
        )
    }

    protected open fun buildPackage(request: PackagePopulateRequest) = with(request) {
        TPackage(
            createdBy = createdBy,
            createdDate = createdDate,
            lastModifiedBy = lastModifiedBy,
            lastModifiedDate = lastModifiedDate,
            projectId = projectId,
            repoName = repoName,
            name = name.trim(),
            description = description,
            key = key.trim(),
            type = type,
            downloads = 0,
            versions = 0,
            versionTag = versionTag.orEmpty(),
            extension = extension.orEmpty()
        )
    }

    protected open fun buildPackageVersion(request: PackageVersionCreateRequest, packageId: String) = with(request) {
        TPackageVersion(
            createdBy = createdBy,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = createdBy,
            lastModifiedDate = LocalDateTime.now(),
            packageId = packageId,
            name = versionName.trim(),
            size = size,
            ordinal = calculateOrdinal(versionName),
            downloads = 0,
            manifestPath = manifestPath,
            artifactPath = artifactPath,
            artifactPaths = artifactPath?.let { mutableSetOf(it) },
            stageTag = stageTag.orEmpty(),
            metadata = MetadataUtils.compatibleConvertAndCheck(metadata, packageMetadata),
            tags = request.tags?.filter { it.isNotBlank() }.orEmpty(),
            extension = request.extension.orEmpty()
        )
    }

    protected open fun buildPackageVersion(
        populatedPackageVersion: PopulatedPackageVersion,
        packageId: String
    ) = with(populatedPackageVersion) {
        TPackageVersion(
            createdBy = createdBy,
            createdDate = createdDate,
            lastModifiedBy = lastModifiedBy,
            lastModifiedDate = lastModifiedDate,
            packageId = packageId,
            name = name.trim(),
            size = size,
            ordinal = calculateOrdinal(name),
            downloads = downloads,
            manifestPath = manifestPath,
            artifactPath = artifactPath,
            stageTag = stageTag.orEmpty(),
            metadata = MetadataUtils.compatibleConvertAndCheck(metadata, packageMetadata),
            extension = extension.orEmpty()
        )
    }

    /**
     * 校验仓库是否存在
     */
    open fun checkRepo(projectId: String, repoName: String): TRepository {
        return repositoryDao.findByNameAndType(projectId, repoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
    }

    /**
     * 计算语义化版本顺序
     */
    private fun calculateOrdinal(versionName: String): Long {
        return try {
            SemVersion.parse(versionName).ordinal()
        } catch (exception: IllegalArgumentException) {
            LOWEST_ORDINAL
        }
    }

    /**
     * 合并version tag
     */
    protected fun mergeVersionTag(
        original: Map<String, String>?,
        extra: Map<String, String>?
    ): Map<String, String> {
        return original?.toMutableMap()?.apply {
            extra?.forEach { (tag, version) -> this[tag] = version }
        }.orEmpty()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PackageBaseService::class.java)
        private const val LOWEST_ORDINAL = 0L
    }
}
