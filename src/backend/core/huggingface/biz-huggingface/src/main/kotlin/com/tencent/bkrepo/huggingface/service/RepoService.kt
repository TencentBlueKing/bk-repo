/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.service

import com.mongodb.DuplicateKeyException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.huggingface.config.HuggingfaceProperties
import com.tencent.bkrepo.huggingface.exception.HfRepoExistException
import com.tencent.bkrepo.huggingface.exception.HfRepoNotFoundException
import com.tencent.bkrepo.huggingface.exception.OperationNotSupportException
import com.tencent.bkrepo.huggingface.pojo.RepoCreateRequest
import com.tencent.bkrepo.huggingface.pojo.RepoDeleteRequest
import com.tencent.bkrepo.huggingface.pojo.RepoMoveRequest
import com.tencent.bkrepo.huggingface.pojo.RepoUpdateRequest
import com.tencent.bkrepo.huggingface.pojo.RepoUrl
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RepoService(
    private val repositoryService: RepositoryService,
    private val packageService: PackageService,
    private val nodeService: NodeService,
    private val huggingfaceProperties: HuggingfaceProperties,
) {

    fun create(request: RepoCreateRequest): RepoUrl {
        with(request) {
            val repository = checkRepository(projectId, repoName)
            return when (repository.category) {
                RepositoryCategory.LOCAL -> {
                    createPackage(request)
                }
                RepositoryCategory.COMPOSITE -> {
                    createPackage(request)
                }
                else -> {
                    throw OperationNotSupportException()
                }
            }
        }
    }

    /**
     * 模型的可访问权限与hf官方不同,
     * 由制品库仓库的访问权限决定。
     * 只是记录用户设置，保障api调用成功，不会改变访问权限
     */
    fun update(request: RepoUpdateRequest) {
        with(request) {
            val repository = checkRepository(projectId, repoName)
            if (repository.category == RepositoryCategory.REMOTE) {
                throw OperationNotSupportException()
            }
            val packageKey = PackageKeys.ofHuggingface(request.type!!, request.repoId!!)
            val packageSummary = packageService.findPackageByKey(projectId, repoName, packageKey)
                ?: throw HfRepoNotFoundException(request.repoId!!)
            val extension = packageSummary.extension.toMutableMap()
            request.private?.let { extension[RepoUpdateRequest::private.name] = it }
            request.gated?.let { extension[RepoUpdateRequest::gated.name] = it }
            val packageUpdateRequest = PackageUpdateRequest(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                name = request.repoId,
                extension = extension
            )
            packageService.updatePackage(packageUpdateRequest)
        }
    }

    fun move(request: RepoMoveRequest) {
        with(request) {
            val repository = checkRepository(projectId, repoName)
            if (repository.category == RepositoryCategory.REMOTE) {
                throw OperationNotSupportException()
            }
            val updateRequest = PackageUpdateRequest(
                projectId = projectId,
                repoName = repoName,
                packageKey = PackageKeys.ofHuggingface(request.type, request.fromRepo),
                name = request.toRepo,
                key = PackageKeys.ofHuggingface(request.type, request.toRepo),
            )
            val renameRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = "/${request.fromRepo}",
                newFullPath = "/${request.toRepo}",
                operator = SecurityUtils.getUserId()
            )
            try {
                packageService.updatePackage(updateRequest)
                nodeService.renameNode(renameRequest)
            } catch (_: DuplicateKeyException) {
                throw HfRepoExistException(request.toRepo)
            }
        }
    }

    fun delete(request: RepoDeleteRequest) {
        val userId = SecurityUtils.getUserId()
        val packageKey = PackageKeys.ofHuggingface(request.type, request.repoId)
        if (request.revision == null) {
            // 删除repo(package)
            packageService.findPackageByKey(request.projectId, request.repoName, packageKey)
                ?: throw HfRepoNotFoundException(request.repoId)
            packageService.deletePackage(request.projectId, request.repoName, packageKey)
            nodeService.deleteNode(
                NodeDeleteRequest(
                    projectId = request.projectId,
                    repoName = request.repoName,
                    fullPath = "/${request.repoId}",
                    operator = userId,
                )
            )
        } else {
            // 删除revision(version)
            packageService.findVersionByName(request.projectId, request.repoName, packageKey, request.revision!!)
                ?: throw VersionNotFoundException("${request.repoId}/${request.revision}")
            packageService.deleteVersion(request.projectId, request.repoName, packageKey, request.revision!!)
            val deleteRequest = NodesDeleteRequest(
                projectId = request.projectId,
                repoName = request.repoName,
                fullPaths = listOf(
                    "/${request.repoId}/resolve/${request.revision}",
                    "/${request.repoId}/info/${request.revision}"
                ),
                operator = userId
            )
            nodeService.deleteNodes(deleteRequest)
        }
    }

    private fun checkRepository(projectId: String, repoName: String): RepositoryDetail {
        return repositoryService.getRepoDetail(projectId, repoName)
            ?: throw HfRepoNotFoundException("$projectId/$repoName")
    }

    private fun createPackage(request: RepoCreateRequest): RepoUrl {
        with(request) {
            val packageCreateRequest = PackageCreateRequest(
                projectId = projectId,
                repoName = repoName,
                packageName = repoId,
                packageKey = PackageKeys.ofHuggingface(type, repoId),
                packageType = PackageType.HUGGINGFACE,
                createdBy = SecurityUtils.getUserId(),
                packageExtension = mapOf(RepoCreateRequest::private.name to private)
            )
            packageService.createPackage(packageCreateRequest)
            return RepoUrl(
                url = "$type/$repoId",
                endpoint = "${huggingfaceProperties.domain}/$projectId/$repoName",
                repoType = request.type,
                repoId = repoId
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RepoService::class.java)
    }
}
