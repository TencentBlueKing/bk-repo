/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.repository.virtual

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.constant.TRAVERSED_LIST
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualRepositoryMember
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * 虚拟仓库抽象逻辑
 */
abstract class VirtualRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var permissionManager: PermissionManager

    override fun query(context: ArtifactQueryContext): Any? {
        val localResultList = mapEachSubRepo(context = context, category = RepositoryCategory.LOCAL) {
            require(it is ArtifactQueryContext)
            val repository = ArtifactContextHolder.getRepository(RepositoryCategory.LOCAL)
            repository.query(it)
        }
        val remoteResultList = mutableListOf<Any>()
        mapFirstRepo(context = context, category = RepositoryCategory.REMOTE) {
            require(it is ArtifactQueryContext)
            val repository = ArtifactContextHolder.getRepository(RepositoryCategory.REMOTE)
            repository.query(it)
        }?.let { remoteResultList.add(it) }
        return Pair(localResultList, remoteResultList)
    }

    override fun search(context: ArtifactSearchContext): List<Any> {
        return mapFirstRepo(context) {
            require(it is ArtifactSearchContext)
            val repository = ArtifactContextHolder.getRepository(it.repositoryDetail.category)
            repository.search(it)
        }.orEmpty()
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return mapFirstRepo(context, false) {
            require(it is ArtifactDownloadContext)
            val category = it.repositoryDetail.category
            val repository = ArtifactContextHolder.getRepository(category) as AbstractArtifactRepository
            repository.onDownload(it)
        }
    }

    override fun onDownloadSuccess(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        throughput: Throughput
    ) {
        val category = context.repositoryDetail.category
        val repository = ArtifactContextHolder.getRepository(category) as AbstractArtifactRepository
        repository.onDownloadSuccess(context, artifactResource, throughput)
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val category = context.repositoryDetail.category
            val repository = ArtifactContextHolder.getRepository(category) as AbstractArtifactRepository
            return repository.buildDownloadRecord(this, artifactResource)
        }
    }

    override fun upload(context: ArtifactUploadContext) {
        context.getVirtualConfiguration().deploymentRepo.takeUnless { it.isNullOrBlank() }?.let {
            val repositoryDetail = repositoryClient.getRepoDetail(context.projectId, it).data!!
            val newContext = context.copy(repositoryDetail) as ArtifactUploadContext
            super.upload(newContext)
        } ?: throw MethodNotAllowedException()
    }

    protected fun getTraversedList(context: ArtifactContext): MutableList<VirtualRepositoryMember> {
        return context.getAttribute(TRAVERSED_LIST) as? MutableList<VirtualRepositoryMember> ?: let {
            val selfRepoInfo = context.repositoryDetail
            val traversedList =
                mutableListOf(VirtualRepositoryMember(selfRepoInfo.name, selfRepoInfo.category))
            context.putAttribute(TRAVERSED_LIST, traversedList)
            return traversedList
        }
    }

    /**
     * 遍历虚拟仓库，直到第一个仓库返回数据
     */
    @Suppress("NestedBlockDepth")
    protected fun <R> mapFirstRepo(
        context: ArtifactContext,
        // 遍历完成后是否恢复为虚拟仓库的上下文
        recoverContext: Boolean = true,
        category: RepositoryCategory? = null,
        action: (ArtifactContext) -> R?
    ): R? {
        val originRepoDetail = context.repositoryDetail
        val repoList = queryMemberList(context, category)
        val traversedList = getTraversedList(context)
        for (member in repoList) {
            if (member in traversedList) {
                continue
            }
            traversedList.add(member)
            try {
                permissionManager.checkRepoPermission(PermissionAction.READ, context.projectId, member.name)
                val subRepoDetail = repositoryClient.getRepoDetail(context.projectId, member.name).data!!
                modifyContext(context, subRepoDetail)
                action(context)?.let {
                    if (recoverContext) {
                        modifyContext(context, originRepoDetail)
                    }
                    return it
                }
            } catch (ignored: Exception) {
                logger.warn("Failed to execute map with repo[$member]: ${ignored.message}")
            }
        }
        return null
    }

    /**
     * 遍历虚拟仓库包含的一种类型的或全部类型的子仓库，执行[action]操作，并将结果聚合成[List]返回
     * category为null时，遍历所有仓库类型
     */
    protected fun <R> mapEachSubRepo(
        context: ArtifactContext,
        // 遍历完成后是否恢复为虚拟仓库的上下文
        recoverContext: Boolean = true,
        category: RepositoryCategory? = null,
        action: (ArtifactContext) -> R?
    ): MutableList<R> {
        val originRepoDetail = context.repositoryDetail
        val repoList = queryMemberList(context, category)
        val traversedList = getTraversedList(context)
        val mapResult = mutableListOf<R>()
        for (member in repoList) {
            if (member in traversedList) {
                continue
            }
            traversedList.add(member)
            try {
                permissionManager.checkRepoPermission(PermissionAction.READ, context.projectId, member.name)
                val subRepoDetail = repositoryClient.getRepoDetail(context.projectId, member.name).data!!
                modifyContext(context, subRepoDetail)
                action(context)?.let { mapResult.add(it) }
            } catch (ignored: Exception) {
                logger.warn("Failed to execute map with repo[$member]: ${ignored.message}")
            }
        }
        if (recoverContext) {
            modifyContext(context, originRepoDetail)
        }
        return mapResult
    }

    /**
     * 遍历虚拟仓库包含的本地仓库（所有）、远程仓库（直到第一个远程仓库返回数据），执行[action]操作，并将结果聚合成[List]返回
     */
    protected fun <R> mapEachLocalAndFirstRemote(
        context: ArtifactContext,
        // 遍历完成后是否恢复为虚拟仓库的上下文
        recoverContext: Boolean = true,
        action: (ArtifactContext) -> R?
    ): MutableList<R> {
        val originRepoDetail = context.repositoryDetail
        val repoList = queryMemberList(context)
        val traversedList = getTraversedList(context)
        val mapResult = mutableListOf<R>()
        var remoteSuccess = false
        for (member in repoList) {
            if (member in traversedList || (remoteSuccess && member.category == RepositoryCategory.REMOTE)) {
                continue
            }
            traversedList.add(member)
            try {
                permissionManager.checkRepoPermission(PermissionAction.READ, context.projectId, member.name)
                val subRepoDetail = repositoryClient.getRepoDetail(context.projectId, member.name).data!!
                modifyContext(context, subRepoDetail)
                action(context)?.let {
                    mapResult.add(it)
                    remoteSuccess = remoteSuccess || member.category == RepositoryCategory.REMOTE
                }
            } catch (ignored: Exception) {
                logger.warn("Failed to execute map with repo[$member]: ${ignored.message}")
            }
        }
        if (recoverContext) {
            modifyContext(context, originRepoDetail)
        }
        return mapResult
    }

    private fun queryMemberList(
        context: ArtifactContext,
        category: RepositoryCategory? = null
    ): List<VirtualRepositoryMember> {
        if (context.repositoryDetail.category != RepositoryCategory.VIRTUAL) {
            modifyContext(context, HttpContextHolder.getRequest().getAttribute(REPO_KEY) as RepositoryDetail)
        }
        val virtualConfiguration = context.getVirtualConfiguration()
        return virtualConfiguration.repositoryList.filter { repo -> category?.run { repo.category == this } ?: true }
    }

    protected fun modifyContext(context: ArtifactContext, repoDetail: RepositoryDetail) {
        context.repositoryDetail = repoDetail
        context.storageCredentials = repoDetail.storageCredentials
        context.artifactInfo.repoName = repoDetail.name
        context.repoName = repoDetail.name
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VirtualRepository::class.java)
    }
}
