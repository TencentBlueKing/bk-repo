/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.opdata.pojo.CleanupRules
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import org.springframework.stereotype.Service

@Service
class RepoService(
    private val projectClient: ProjectClient,
    private val repoClient: RepositoryClient,
) {

    fun batchUpdateCleanupStrategy(rule: CleanupRules) {
        rule.specialRepoRules.forEach { (repoStr, rDays) ->
            val (projectId, repoName) = repoStr.split(StringPool.SLASH)
            updateRepo(projectId, repoName, rDays)
        }

        var offset = 0L
        var projects: List<ProjectInfo?>?
        do {
            projects = projectClient.rangeQuery(
                ProjectRangeQueryRequest(projectIds = emptyList(), offset = offset, limit = 1000)
            ).data?.records
            projects?.forEach {
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
                val value = rule.bgRepoRules[key] ?: rule.defaultDays
                val (_, repoName) = key.split(StringPool.SLASH)
                updateRepo(projectInfo.name, repoName, value)
                return
            }
        }
        updateRepo(projectInfo.name, rule.defaultRepoName, rule.defaultDays)
    }


    private fun updateRepo(
        projectId: String,
        repoName: String?,
        cleanupValue: Long,
    ) {
        if (repoName.isNullOrEmpty()) return
        val repoInfo = repoClient.getRepoInfo(projectId, repoName).data ?: return
        val configuration = repoInfo.configuration
        val cleanupStrategy = CleanupStrategy(
            cleanupType = "retentionDays",
            cleanupValue = cleanupValue.toString(),
            enable = true
        )
        configuration.settings["cleanupStrategy"] = cleanupStrategy
        val request = RepoUpdateRequest(
            projectId = repoInfo.projectId,
            name = repoInfo.name,
            configuration = configuration,
            operator = SYSTEM_USER
        )
        repoClient.updateRepo(request)
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

}
