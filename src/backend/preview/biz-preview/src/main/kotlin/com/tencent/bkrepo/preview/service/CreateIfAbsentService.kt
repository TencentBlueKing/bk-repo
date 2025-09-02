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

package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CreateIfAbsentService(
    private val config: PreviewConfig,
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CreateIfAbsentService::class.java)
    }

    private val repoQuotaBytes: Long
        get() = config.repoQuota * 1024 * 1024

    /**
     * 如果project不存在则创建
     */
    fun createProjectIfAbsent() {
        val projectId = config.projectId
        if (projectService.checkExist(projectId)) {
            return
        }
        val req = ProjectCreateRequest(name = projectId, displayName = projectId)
        val createdProject = projectService.createProject(req)
        logger.info("Created project successfully, projectId: ${createdProject.name}")
    }

    /**
     * 如果repository不存在则创建
     */
    fun createRepoIfAbsent() {
        val tenanId = ProjectServiceHelper.getTenantId()
        val projectId = if (tenanId.isNullOrEmpty()) config.projectId else "$tenanId.${config.projectId}"
        val repoName = config.repoName

        if (!repositoryService.checkExist(projectId, repoName, RepositoryType.GENERIC.name)) {
            val req = RepoCreateRequest(
                projectId = projectId,
                name = repoName,
                type = RepositoryType.GENERIC,
                category = RepositoryCategory.LOCAL,
                public = config.repoPublic,
                quota = repoQuotaBytes,
                configuration = buildRepoConfig()
            )
            val createdRepo = repositoryService.createRepo(req)
            logger.info("Created repository successfully, projectId: ${createdRepo.projectId}, " +
                    "repoName: ${createdRepo.name}")
            return
        }

        updateRepoIfNeeded(projectId, repoName, RepositoryType.GENERIC.name)
    }

    /**
     * 构造repository配置
     */
    private fun buildRepoConfig(): LocalConfiguration {
        val repoConfig = LocalConfiguration()
        val cleanupStrategy = mutableMapOf(
            "enable" to true,
            "cleanupType" to "retentionDays",
            "cleanupValue" to config.artifactKeepDays
        )
        repoConfig.settings["cleanupStrategy"] = cleanupStrategy
        return repoConfig
    }

    /**
     * 如果repository基础属性有变化则更新
     */
    private fun updateRepoIfNeeded(projectId: String, repoName: String, type: String) {
        repositoryService.getRepoInfo(projectId, repoName, type)?.let { existing ->
            val needUpdate = existing.public != config.repoPublic || existing.quota != repoQuotaBytes
            if (!needUpdate) return

            logger.info("Repository changed, updating: projectId=$projectId, repoName=$repoName")
            val updateReq = RepoUpdateRequest(
                projectId = projectId,
                name = repoName,
                public = config.repoPublic,
                quota = repoQuotaBytes,
                configuration = buildRepoConfig(),
                operator = SYSTEM_USER
            )
            repositoryService.updateRepo(updateReq)
        }
    }
}