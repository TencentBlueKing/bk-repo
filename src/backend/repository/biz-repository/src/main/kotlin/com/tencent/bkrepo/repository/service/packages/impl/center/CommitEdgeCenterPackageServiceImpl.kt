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

package com.tencent.bkrepo.repository.service.packages.impl.center

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeCenterCondition
import com.tencent.bkrepo.repository.dao.PackageDao
import com.tencent.bkrepo.repository.dao.PackageVersionDao
import com.tencent.bkrepo.repository.model.ClusterResource
import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackagePopulateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PopulatedPackageVersion
import com.tencent.bkrepo.repository.search.packages.PackageSearchInterpreter
import com.tencent.bkrepo.repository.service.packages.impl.PackageServiceImpl
import com.tencent.bkrepo.repository.util.ClusterUtils
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

/**
 * Star组网方式的Center节点Package管理服务
 */
@Service
@Conditional(CommitEdgeCenterCondition::class)
class CommitEdgeCenterPackageServiceImpl(
    packageDao: PackageDao,
    packageVersionDao: PackageVersionDao,
    packageSearchInterpreter: PackageSearchInterpreter,
    private val clusterProperties: ClusterProperties
) : PackageServiceImpl(
    packageDao,
    packageVersionDao,
    packageSearchInterpreter,
) {
    override fun buildPackage(request: PackageVersionCreateRequest): TPackage {
        return super.buildPackage(request).also { addSrcRegionToResource(it) }
    }

    override fun buildPackage(request: PackagePopulateRequest): TPackage {
        return super.buildPackage(request).also { addSrcRegionToResource(it) }
    }

    /**
     * 获取已存在的Package或创建Package，会将当前的操作来源region添加到package region中
     */
    override fun findOrCreatePackage(tPackage: TPackage): TPackage {
        with(tPackage) {
            val savedPackage = packageDao.findByKey(projectId, repoName, key)
            val srcRegion = srcRegion()

            if (savedPackage != null &&
                srcRegion.isNotEmpty() &&
                savedPackage.clusterNames?.contains(srcRegion) == false) {
                val result = packageDao.addClusterByKey(projectId, repoName, key, srcRegion)
                addSrcRegionToResource(savedPackage, srcRegion)
                logger.info("Update package[$tPackage] region[$srcRegion] result[${result?.modifiedCount}]")
            }

            return savedPackage ?: createPackage(tPackage)
        }
    }

    override fun buildPackageVersion(request: PackageVersionCreateRequest, packageId: String): TPackageVersion {
        return super.buildPackageVersion(request, packageId).also { addSrcRegionToResource(it) }
    }

    override fun buildPackageVersion(
        populatedPackageVersion: PopulatedPackageVersion,
        packageId: String
    ): TPackageVersion {
        return super.buildPackageVersion(populatedPackageVersion, packageId).also { addSrcRegionToResource(it) }
    }

    /**
     * 只允许覆盖节点自身创建的包
     */
    override fun checkPackageVersionOverwrite(overwrite: Boolean, packageName: String, oldVersion: TPackageVersion) {
        ClusterUtils.checkIsSrcRegion(oldVersion.clusterNames)
        super.checkPackageVersionOverwrite(overwrite, packageName, oldVersion)
    }

    override fun populateRegion(tPackage: TPackage) {
        with(tPackage) {
            val srcRegion = srcRegion()
            if (tPackage.clusterNames?.contains(srcRegion) == false) {
                packageDao.addClusterByKey(projectId, repoName, key, srcRegion)
            }
            tPackage.clusterNames = tPackage.clusterNames.orEmpty() + srcRegion
        }
    }

    override fun checkRegion(clusterResource: ClusterResource) {
        ClusterUtils.checkIsSrcRegion(clusterResource.readClusterNames())
    }

    override fun deletePackage(projectId: String, repoName: String, packageKey: String, realIpAddress: String?) {
        val tPackage = packageDao.findByKey(projectId, repoName, packageKey) ?: return
        val srcRegion = srcRegion()

        // 是Package唯一的region时可以直接删除
        if (ClusterUtils.isUniqueSrcCluster(tPackage.clusterNames)) {
            super.deletePackage(projectId, repoName, packageKey, realIpAddress)
        }

        // Package包含region，但不是Package的唯一region时只能清理单个region的值
        if (ClusterUtils.containsSrcCluster(tPackage.clusterNames)) {
            packageDao.removeClusterByKey(projectId, repoName, packageKey, srcRegion)
            packageVersionDao.deleteByPackageIdAndClusterName(tPackage.id!!, srcRegion)
            logger.info("Remove package [$projectId/$repoName/$packageKey] region[$srcRegion] success")
        }

        // Package不包含region时候直接报错
        throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
    }

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String,
        realIpAddress: String?
    ) {
        val tPackage = packageDao.findByKey(projectId, repoName, packageKey) ?: return
        val tPackageVersion = packageVersionDao.findByName(tPackage.id.orEmpty(), versionName) ?: return
        val srcRegion = srcRegion()

        if (ClusterUtils.isUniqueSrcCluster(tPackageVersion.clusterNames)) {
            super.deleteVersion(projectId, repoName, packageKey, versionName, realIpAddress)
            return
        }

        if (ClusterUtils.containsSrcCluster(tPackageVersion.clusterNames)) {
            packageVersionDao.removeClusterByKey(tPackageVersion.packageId, srcRegion)
            if (!packageVersionDao.existsByPackageIdAndClusterName(tPackageVersion.packageId, srcRegion)) {
                packageDao.removeClusterByKey(projectId, repoName, packageKey, srcRegion)
            }
            return
        }

        // Package不包含region时候直接报错
        throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
    }

    private fun createPackage(tPackage: TPackage): TPackage {
        val srcRegion = srcRegion()
        with(tPackage) {
            try {
                val savedPackage = packageDao.save(tPackage)
                logger.info("Create package[$tPackage] success")
                return savedPackage
            } catch (exception: DuplicateKeyException) {
                logger.warn("Create package[$tPackage] error: [${exception.message}]")
                val result = packageDao.addClusterByKey(projectId, repoName, key, srcRegion)
                logger.info("Update package[$tPackage] region[$srcRegion] result[${result?.modifiedCount}]")
            }
            return packageDao.findByKey(projectId, repoName, key)!!
        }
    }

    private fun addSrcRegionToResource(clusterResource: ClusterResource, srcRegion: String = srcRegion()) {
        val oldRegion = clusterResource.readClusterNames() ?: mutableSetOf()
        clusterResource.writeClusterNames(oldRegion + srcRegion)
    }

    private fun srcRegion() = SecurityUtils.getClusterName() ?: clusterProperties.region!!

    companion object {
        private val logger = LoggerFactory.getLogger(CommitEdgeCenterPackageServiceImpl::class.java)
    }
}
