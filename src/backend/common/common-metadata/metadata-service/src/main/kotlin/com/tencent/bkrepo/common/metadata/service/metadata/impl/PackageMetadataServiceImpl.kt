/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.metadata.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.dao.packages.PackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.service.metadata.PackageMetadataService
import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.common.metadata.util.PackageQueryHelper
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataSaveRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 元数据服务实现类
 */
@Service
@Conditional(SyncCondition::class)
class PackageMetadataServiceImpl(
    private val packageDao: PackageDao,
    private val packageVersionDao: PackageVersionDao
) : PackageMetadataService {

    override fun listMetadata(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String
    ): Map<String, Any> {
        val tPackage = getPackage(projectId, repoName, packageKey)
        return MetadataUtils.toMap(packageVersionDao.findByName(tPackage.id!!, version)?.metadata)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun saveMetadata(request: PackageMetadataSaveRequest) {
        with(request) {
            if (versionMetadata.isNullOrEmpty()) {
                logger.info("Metadata key list is empty, skip saving[$this]")
                return
            }
            val tPackage = getPackage(projectId, repoName, packageKey)
            val tPackageVersion = getPackageVersion(tPackage.id!!, version)
            val oldMetadata = tPackageVersion.metadata
            val newMetadata = versionMetadata!!.map { MetadataUtils.convertAndCheck(it) }
            tPackageVersion.metadata = MetadataUtils.merge(oldMetadata, newMetadata)
            packageVersionDao.save(tPackageVersion)
            logger.info("Save package metadata [$this] success.")
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteMetadata(request: PackageMetadataDeleteRequest, allowDeleteSystemMetadata: Boolean) {
        with(request) {
            if (keysToDelete.isEmpty()) {
                logger.info("Metadata key list is empty, skip deleting")
                return
            }
            val tPackage = getPackage(projectId, repoName, packageKey)
            val tPackageVersion = getPackageVersion(tPackage.id!!, version)
            // 检查是否有更新权限
            ClusterUtils.checkContainsSrcCluster(tPackageVersion.clusterNames)
            tPackageVersion.metadata.forEach {
                if (it.key in keysToDelete && it.system && !allowDeleteSystemMetadata) {
                    throw PermissionException("No permission to update system metadata[${it.key}]")
                }
            }

            val update = Update().pull(
                TPackageVersion::metadata.name,
                Query.query(where(TMetadata::key).inValues(keysToDelete))
            )
            packageVersionDao.updateFirst(PackageQueryHelper.versionQuery(tPackage.id!!, name = version), update)
            logger.info("Delete metadata[$keysToDelete] on pkg[/$projectId/$repoName/$packageKey:$version] success.")
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun addForbidMetadata(request: PackageMetadataSaveRequest) {
        with(request) {
            val forbidMetadata = MetadataUtils.extractForbidMetadata(versionMetadata!!)
            if (forbidMetadata.isNullOrEmpty()) {
                logger.info("forbidMetadata is empty, skip saving[$this]")
                return
            }
            saveMetadata(request.copy(versionMetadata = forbidMetadata))
        }
    }

    /**
     * 查找包，不存在则抛异常
     */
    private fun getPackage(projectId: String, repoName: String, packageKey: String): TPackage {
        return packageDao.findByKey(projectId, repoName, packageKey)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, packageKey)
    }

    /**
     * 查找版本，不存在则抛异常
     */
    private fun getPackageVersion(packageId: String, versionName: String): TPackageVersion {
        return packageVersionDao.findByName(packageId, versionName)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, versionName)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PackageMetadataServiceImpl::class.java)
    }
}
