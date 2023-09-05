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
import com.tencent.bkrepo.common.artifact.util.ClusterUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeCenterCondition
import com.tencent.bkrepo.repository.dao.PackageDao
import com.tencent.bkrepo.repository.dao.PackageVersionDao
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.model.ClusterResource
import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.packages.request.PackagePopulateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PopulatedPackageVersion
import com.tencent.bkrepo.repository.search.packages.PackageSearchInterpreter
import com.tencent.bkrepo.repository.service.packages.impl.PackageServiceImpl
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

/**
 * CommitEdge组网方式的Center节点Package管理服务
 */
@Service
@Conditional(CommitEdgeCenterCondition::class)
class CommitEdgeCenterPackageServiceImpl(
    repositoryDao: RepositoryDao,
    packageDao: PackageDao,
    packageVersionDao: PackageVersionDao,
    packageSearchInterpreter: PackageSearchInterpreter,
    private val clusterProperties: ClusterProperties
) : PackageServiceImpl(
    repositoryDao,
    packageDao,
    packageVersionDao,
    packageSearchInterpreter,
) {
    override fun buildPackage(request: PackageVersionCreateRequest): TPackage {
        return super.buildPackage(request).also { addSrcClusterToResource(it) }
    }

    override fun buildPackage(request: PackagePopulateRequest): TPackage {
        return super.buildPackage(request).also { addSrcClusterToResource(it) }
    }

    /**
     * 获取已存在的Package或创建Package，会将当前的操作来源cluster添加到package cluster中
     * 目前依赖源仓库只允许属于一个cluster，所以packageCluster中只会有一个值
     */
    override fun findOrCreatePackage(tPackage: TPackage): TPackage {
        with(tPackage) {
            checkRepo(projectId, repoName)
            val savedPackage = packageDao.findByKey(projectId, repoName, key)
            val srcCluster = srcCluster()

            if (savedPackage != null &&
                srcCluster.isNotEmpty() &&
                savedPackage.clusterNames?.contains(srcCluster) == false) {
                val result = packageDao.addClusterByKey(projectId, repoName, key, srcCluster)
                addSrcClusterToResource(savedPackage, srcCluster)
                logger.info("Update package[$tPackage] cluster[$srcCluster] result[${result?.modifiedCount}]")
            }

            return savedPackage ?: createPackage(tPackage)
        }
    }

    override fun buildPackageVersion(request: PackageVersionCreateRequest, packageId: String): TPackageVersion {
        return super.buildPackageVersion(request, packageId).also { addSrcClusterToResource(it) }
    }

    override fun buildPackageVersion(
        populatedPackageVersion: PopulatedPackageVersion,
        packageId: String
    ): TPackageVersion {
        return super.buildPackageVersion(populatedPackageVersion, packageId).also { addSrcClusterToResource(it) }
    }

    /**
     * 只允许覆盖节点自身创建的包
     */
    override fun checkPackageVersionOverwrite(overwrite: Boolean, packageName: String, oldVersion: TPackageVersion) {
        ClusterUtils.checkIsSrcCluster(oldVersion.clusterNames)
        super.checkPackageVersionOverwrite(overwrite, packageName, oldVersion)
    }

    override fun populateCluster(tPackage: TPackage) {
        with(tPackage) {
            val srcCluster = srcCluster()
            if (tPackage.clusterNames?.contains(srcCluster) == false) {
                packageDao.addClusterByKey(projectId, repoName, key, srcCluster)
            }
            tPackage.clusterNames = tPackage.clusterNames.orEmpty() + srcCluster
        }
    }

    override fun checkCluster(clusterResource: ClusterResource) {
        ClusterUtils.checkIsSrcCluster(clusterResource.readClusterNames())
    }

    override fun deletePackage(projectId: String, repoName: String, packageKey: String, realIpAddress: String?) {
        val tPackage = packageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey) ?: return
        val srcCluster = srcCluster()

        if (ClusterUtils.isUniqueSrcCluster(tPackage.clusterNames)) {
            // 是Package唯一的cluster时可以直接删除
            super.deletePackage(projectId, repoName, packageKey, realIpAddress)
        } else if (ClusterUtils.containsSrcCluster(tPackage.clusterNames)) {
            // Package包含cluster，但不是Package的唯一cluster时只能清理单个cluster的值
            packageDao.removeClusterByKey(projectId, repoName, packageKey, srcCluster)
            // 因为目前packageVersion只会属于一个cluster,所以此处可以直接删除与该cluster关联的所有packageVersion
            packageVersionDao.deleteByPackageIdAndClusterName(tPackage.id!!, srcCluster)
            logger.info("Remove package [$projectId/$repoName/$packageKey] cluster[$srcCluster] success")
        } else {
            // Package不包含cluster时候直接报错
            throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
        }

    }

    override fun checkRepo(projectId: String, repoName: String): TRepository {
        val repo = super.checkRepo(projectId, repoName)
        if (!ClusterUtils.containsSrcCluster(repo.clusterNames)) {
            throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
        }
        return repo
    }

    private fun createPackage(tPackage: TPackage): TPackage {
        val srcCluster = srcCluster()
        with(tPackage) {
            try {
                val savedPackage = packageDao.save(tPackage)
                logger.info("Create package[$tPackage] success")
                return savedPackage
            } catch (exception: DuplicateKeyException) {
                logger.warn("Create package[$tPackage] error: [${exception.message}]")
                val result = packageDao.addClusterByKey(projectId, repoName, key, srcCluster)
                logger.info("Update package[$tPackage] cluster[$srcCluster] result[${result?.modifiedCount}]")
            }
            return packageDao.findByKey(projectId, repoName, key)!!
        }
    }

    private fun addSrcClusterToResource(clusterResource: ClusterResource, srcCluster: String = srcCluster()) {
        val oldCluster = clusterResource.readClusterNames() ?: mutableSetOf()
        clusterResource.writeClusterNames(oldCluster + srcCluster)
    }

    private fun srcCluster() = SecurityUtils.getClusterName() ?: clusterProperties.self.name!!

    companion object {
        private val logger = LoggerFactory.getLogger(CommitEdgeCenterPackageServiceImpl::class.java)
    }
}
