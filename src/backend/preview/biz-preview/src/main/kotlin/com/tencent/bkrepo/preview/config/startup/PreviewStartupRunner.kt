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

package com.tencent.bkrepo.preview.config.startup

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 初始化文件预览需要的项目和仓库
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class PreviewStartupRunner(
    private val config: PreviewConfig,
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService
) : ApplicationRunner {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ApplicationRunner::class.java)
    }

    override fun run(args: ApplicationArguments?) {
        createProject()
        createRepo()
    }

    /**
     * 创建仓库
     */
    private fun createRepo() {
        val projectId = config.projectId
        val repoName = config.repoName
        var exist = repositoryService.checkExist(projectId, repoName, RepositoryType.GENERIC.name)
        val repoConfig = LocalConfiguration()
        val cleanupStrategy = mutableMapOf(
            "enable" to true,
            "cleanupType" to "retentionDays",
            "cleanupValue" to config.artifactKeepDays
        )
        repoConfig.settings["cleanupStrategy"] = cleanupStrategy

        if (!exist) {
            val req = RepoCreateRequest(projectId = config.projectId,
                name = config.repoName,
                type = RepositoryType.GENERIC,
                category = RepositoryCategory.LOCAL,
                public = config.repoPublic,
                quota = config.repoQuota * 1024 * 1024,
                configuration =repoConfig
            )
            var createdRepo = repositoryService.createRepo(req)
            logger.debug("Create project success，projectId:${createdRepo.name}")
        } else {
            logger.debug("project ${config.projectId} and repository ${config.repoName} exist. to update")
            val updateRepo = RepoUpdateRequest(
                projectId = config.projectId,
                name = config.repoName,
                public = config.repoPublic,
                quota = config.repoQuota * 1024 * 1024,
                configuration =repoConfig,
                operator = SYSTEM_USER
            )
            repositoryService.updateRepo(updateRepo)
        }
    }

    /**
     * 创建项目
     */
    private fun createProject() {
        val projectId = config.projectId
        var exist = projectService.checkExist(projectId);
        if (!exist) {
            val req = ProjectCreateRequest(name = config.projectId, displayName = config.projectId)
            var createdProject = projectService.createProject(req)
            logger.debug("Create project success，projectId:${createdProject.name}")
        } else {
            logger.debug("project ${config.projectId} exist skip.")
        }
    }
}