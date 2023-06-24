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
import com.tencent.bkrepo.common.artifact.constant.NODE_DETAIL_KEY
import com.tencent.bkrepo.common.artifact.constant.TRAVERSED_LIST
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryIdentify
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * 虚拟仓库抽象逻辑
 */
abstract class VirtualRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var permissionManager: PermissionManager

    override fun query(context: ArtifactQueryContext): Any? {
        return mapEachLocalAndFirstRemote(context) { sub, repository ->
            require(sub is ArtifactQueryContext)
            repository.query(sub)
        }
    }

    override fun search(context: ArtifactSearchContext): List<Any> {
        return mapFirstRepo(context) { sub, repository ->
            require(sub is ArtifactSearchContext)
            repository.search(sub)
        }.orEmpty()
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return mapFirstRepo(context) { sub, repository ->
            require(sub is ArtifactDownloadContext)
            require(repository is AbstractArtifactRepository)
            repository.onDownload(sub)?.also { context.putAttribute(SUB_CONTEXT, sub) }
        }
    }

    override fun onDownloadSuccess(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        throughput: Throughput
    ) {
        val subContext = context.getAttribute<ArtifactDownloadContext>(SUB_CONTEXT) ?: run {
            val repoDetail = with(artifactResource.srcRepo) {
                repositoryClient.getRepoDetail(projectId, name).data!!
            }
            context.copyBy(repoDetail) as ArtifactDownloadContext
        }
        val category = subContext.repositoryDetail.category
        if (category != RepositoryCategory.VIRTUAL) {
            val repository = ArtifactContextHolder.getRepository(category) as AbstractArtifactRepository
            repository.onDownloadSuccess(subContext, artifactResource, throughput)
        }
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val category = context.repositoryDetail.category
            return if (category != RepositoryCategory.VIRTUAL) {
                val repository = ArtifactContextHolder.getRepository(category) as AbstractArtifactRepository
                repository.buildDownloadRecord(this, artifactResource)
            } else null
        }
    }

    override fun upload(context: ArtifactUploadContext) {
        mapDeploymentRepo(context) { sub, repository ->
            require(sub is ArtifactUploadContext)
            repository.upload(sub)
        }
    }

    protected fun getTraversedList(context: ArtifactContext): MutableList<RepositoryIdentify> {
        return context.getAttribute(TRAVERSED_LIST) as? MutableList<RepositoryIdentify> ?: let {
            val selfRepoInfo = context.repositoryDetail
            val traversedList =
                mutableListOf(RepositoryIdentify(selfRepoInfo.projectId, selfRepoInfo.name, selfRepoInfo.category))
            context.putAttribute(TRAVERSED_LIST, traversedList)
            return traversedList
        }
    }

    /**
     * 遍历虚拟仓库，直到第一个仓库返回数据
     */
    protected fun <R> mapFirstRepo(
        context: ArtifactContext,
        action: (ArtifactContext, ArtifactRepository) -> R?
    ): R? = doMap(context, null, action)

    /**
     * 遍历虚拟仓库包含的本地仓库（所有）、远程仓库（直到第一个远程仓库返回数据），执行[action]操作，并将结果按仓库顺序聚合成[List]返回
     */
    protected fun <R> mapEachLocalAndFirstRemote(
        context: ArtifactContext,
        action: (ArtifactContext, ArtifactRepository) -> R?
    ): MutableList<R> {
        val resultList = mutableListOf<R>()
        doMap(context, resultList, action)
        return resultList
    }

    /**
     * 对默认部署仓库执行[action]操作
     */
    protected fun <R> mapDeploymentRepo(
        context: ArtifactContext,
        action: (ArtifactContext, ArtifactRepository) -> R?
    ): R? {
        return context.getVirtualConfiguration().deploymentRepo.takeUnless { it.isNullOrBlank() }?.let {
            perform(context, RepositoryIdentify(context.projectId, it), context is ArtifactUploadContext, action)
        } ?: throw MethodNotAllowedException()
    }

    @Suppress("NestedBlockDepth")
    private fun <R> doMap(
        context: ArtifactContext,
        resultList: MutableList<R>?,
        action: (ArtifactContext, ArtifactRepository) -> R?
    ): R? {
        val repoList = context.getVirtualConfiguration().repositoryList
        val traversedList = getTraversedList(context)
        var remoteSuccess = false
        for (member in repoList) {
            if (member in traversedList || (remoteSuccess && member.category == RepositoryCategory.REMOTE)) {
                continue
            }
            traversedList.add(member)
            context.request.removeAttribute(NODE_DETAIL_KEY)
            try {
                perform(context, member, action = action)?.let {
                    if (resultList != null) resultList.add(it) else return it
                    remoteSuccess = remoteSuccess || member.category == RepositoryCategory.REMOTE
                }
            } catch (ignored: Exception) {
                logger.warn("Failed to execute map with repo[$member]: ${ignored.message}")
            }
        }
        return null
    }

    private fun <R> perform(
        context: ArtifactContext,
        repo: RepositoryIdentify,
        writeAction: Boolean = false,
        action: (ArtifactContext, ArtifactRepository) -> R?
    ): R? {
        permissionManager.checkRepoPermission(
            if (writeAction) PermissionAction.WRITE else PermissionAction.READ,
            repo.projectId,
            repo.name
        )
        val subRepoDetail = repositoryClient.getRepoDetail(repo.projectId, repo.name).data!!
        val repository = ArtifactContextHolder.getRepository(subRepoDetail.category)
        val subContext = context.copyBy(subRepoDetail)
        return action(subContext, repository)
    }

    companion object {
        private const val SUB_CONTEXT = "subContext"
        private val logger = LoggerFactory.getLogger(VirtualRepository::class.java)
    }
}
