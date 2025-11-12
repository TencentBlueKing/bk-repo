/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.opdata.pojo.CleanupRules
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RepoService(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
) {

    fun batchUpdateCleanupStrategy(rule: CleanupRules) {
        rule.specialRepoRules.forEach { (repoStr, value) ->
            val (projectId, repoName) = repoStr.split(StringPool.SLASH)
            updateRepo(
                projectId = projectId,
                repoNames = listOf(repoName),
                cleanupType = rule.cleanupType,
                cleanupValue = value,
                enable = rule.enable,
                relatedRepo = rule.relatedRepo,
                forceRefresh = rule.forceRefresh,
            )
        }

        var offset = 0L
        var projects: List<ProjectInfo?>?
        do {
            projects = projectService.rangeQuery(
                ProjectRangeQueryRequest(projectIds = emptyList(), offset = offset, limit = 1000)
            ).records
            projects.forEach {
                runReposInProject(it!!, rule)
            }
            offset += 1000
        } while (!projects.isNullOrEmpty())
    }

    private fun runReposInProject(
        projectInfo: ProjectInfo,
        rule: CleanupRules
        ) {
        val bgId = projectInfo.metadata.firstOrNull { it.key == "bgId" }?.value as String?

        if (!bgId.isNullOrEmpty()) {
            val bgRule = rule.bgRepoRules.filterKeys { it.startsWith("$bgId/") }
            if (bgRule.isNotEmpty()) {
                val key = bgRule.keys.first()
                val value = rule.bgRepoRules[key] ?: rule.cleanupValue
                val (_, repoName) = key.split(StringPool.SLASH)
                updateRepo(
                    projectId = projectInfo.name,
                    repoNames = listOf(repoName),
                    cleanupType = rule.cleanupType,
                    cleanupValue = value,
                    enable = rule.enable,
                    specialRepoRules = rule.specialRepoRules,
                    relatedRepo = rule.relatedRepo,
                    forceRefresh = rule.forceRefresh,
                )
                return
            }
        }
        updateRepo(
            projectId = projectInfo.name,
            repoNames = rule.defaultRepos,
            cleanupType = rule.cleanupType,
            cleanupValue = rule.cleanupValue,
            enable = rule.enable,
            specialRepoRules = rule.specialRepoRules,
            relatedRepo = rule.relatedRepo,
            forceRefresh = rule.forceRefresh,
        )
    }


    private fun updateRepo(
        projectId: String,
        repoNames: List<String>?,
        cleanupType: String,
        cleanupValue: String,
        enable: Boolean,
        specialRepoRules: Map<String, String> = emptyMap(),
        relatedRepo: String? = null,
        forceRefresh: Boolean = false
    ) {
        if (repoNames.isNullOrEmpty() || cleanupValue.isEmpty() || cleanupType.isEmpty()) return
        repoNames.forEach {
            if (specialRepoRules.containsKey("$projectId/$it")) return
            val repoInfo = repositoryService.getRepoInfo(projectId, it) ?: return
            val configuration = repoInfo.configuration
            if (configuration.settings["cleanupStrategy"] != null && !forceRefresh) return
            var useDefault = true
            if (!relatedRepo.isNullOrEmpty()) {
                val relatedConfig = repositoryService.getRepoInfo(projectId, relatedRepo)?.configuration
                    ?.getSetting<Map<String, Any>>("cleanupStrategy")
                val relatedCleanupStrategy = toCleanupStrategy(relatedConfig)
                if (relatedCleanupStrategy != null) {
                    configuration.settings["cleanupStrategy"] = relatedCleanupStrategy
                    useDefault = false
                }
            }
            if (useDefault) {
                val cleanupStrategy = CleanupStrategy(
                    cleanupType = cleanupType,
                    cleanupValue = cleanupValue,
                    enable = enable
                )
                configuration.settings["cleanupStrategy"] = cleanupStrategy
            }
            logger.info("will update cleanup strategy for $projectId|${repoInfo.name}")
            val request = RepoUpdateRequest(
                projectId = repoInfo.projectId,
                name = repoInfo.name,
                configuration = configuration,
                operator = SYSTEM_USER
            )
            repositoryService.updateRepo(request)
        }
    }

    private fun toCleanupStrategy(map: Map<String, Any>?): Any? {
        if (map.isNullOrEmpty()) return null
        val cleanupStrategy = CleanupStrategy(
            enable = map[CleanupStrategy::enable.name] as? Boolean ?: false,
            cleanupType = map[CleanupStrategy::cleanupType.name] as? String,
            cleanupValue = map[CleanupStrategy::cleanupValue.name]?.toString(),
            cleanTargets = map[CleanupStrategy::cleanTargets.name] as? List<String>,
        )
        if (cleanupStrategy.cleanupType.isNullOrEmpty() || cleanupStrategy.cleanupValue.isNullOrEmpty())
            return null
        return cleanupStrategy
    }

    data class CleanupStrategy(
        // 清理策略类型
        val cleanupType: String? = null,
        // 清理策略类型对应的实际值
        val cleanupValue: String? = null,
        // 指定路径或者package
        val cleanTargets: List<String>? = null,
        // 是否启用
        val enable: Boolean = false
    )
    companion object {
        private val logger = LoggerFactory.getLogger(RepoService::class.java)
    }
}
