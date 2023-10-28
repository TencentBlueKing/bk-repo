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

package com.tencent.bkrepo.opdata.handler.impl

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.opdata.constant.DOCKER_TYPES
import com.tencent.bkrepo.opdata.constant.FILTER_TYPE
import com.tencent.bkrepo.opdata.constant.FILTER_VALUE
import com.tencent.bkrepo.opdata.model.StatDateModel
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.RepoMetrics
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.FilterType
import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository

open class BaseHandler(
    private val projectMetricsRepository: ProjectMetricsRepository,
    private val statDateModel: StatDateModel
) {

    fun calculateMetricValue(target: Target): Long {
        val (filterType, filterValue) = getFilterInfo(target)
        val projects = projectMetricsRepository.findAllByCreatedDate(statDateModel.getShedLockInfo())
        return when (filterType) {
            FilterType.REPO_TYPE -> {
                calculateMetrics(projects, target.target, filterValue)
            }
            FilterType.REPO_NAME -> {
                calculateMetrics(projects, target.target, repoName = filterValue)
            }
            else -> {
                calculateMetrics(projects, target.target)
            }
        }
    }

    fun calculateMetricMap(target: Target): HashMap<String, Long> {
        val (filterType, filterValue) = getFilterInfo(target)
        val projects = projectMetricsRepository.findAllByCreatedDate(statDateModel.getShedLockInfo())
        return when (filterType) {
            FilterType.REPO_TYPE -> {
                calculateMetricsMap(projects, target.target, filterValue)
            }
            FilterType.REPO_NAME -> {
                calculateMetricsMap(projects, target.target, repoName = filterValue)
            }
            else -> {
                calculateMetricsMap(projects, target.target)
            }
        }
    }

    private fun calculateMetrics(
        projects: List<TProjectMetrics>, metrics: Metrics,
        repoType: String? = null, repoName: String? = null
    ): Long {
        val repoTypeList = when (repoType) {
            RepositoryType.DOCKER.name -> {
                DOCKER_TYPES
            }
            null -> null
            else -> listOf(repoType)
        }
        var result = 0L
        projects.forEach { project ->
            if (repoTypeList.isNullOrEmpty() && repoName.isNullOrEmpty()) {
                when (metrics) {
                    Metrics.CAPSIZE -> {
                        result += project.capSize
                    }
                    Metrics.NODENUM -> {
                        result += project.nodeNum
                    }
                    else -> {}
                }
            } else {
                project.repoMetrics.filter {
                    filterRepo(repoTypeList, repoName, it)
                }.forEach { repo ->
                    when (metrics) {
                        Metrics.CAPSIZE -> {
                            result += repo.size
                        }
                        Metrics.NODENUM -> {
                            result += repo.num
                        }
                        else -> {}
                    }
                }
            }
        }
        return result
    }

    private fun calculateMetricsMap(
        projects: List<TProjectMetrics>, metrics: Metrics,
        repoType: String? = null, repoName: String? = null
    ): HashMap<String, Long> {
        val tmpMap = HashMap<String, Long>()
        val repoTypeList = when (repoType) {
            RepositoryType.DOCKER.name -> {
                DOCKER_TYPES
            }
            null -> null
            else -> listOf(repoType)
        }
        projects.forEach { project ->
            if (repoTypeList.isNullOrEmpty() && repoName.isNullOrEmpty()) {
                when (metrics) {
                    Metrics.PROJECTNODESIZE -> {
                        tmpMap[project.projectId] = project.capSize
                    }
                    Metrics.PROJECTNODENUM -> {
                        tmpMap[project.projectId] = project.nodeNum
                    }
                    else -> {}
                }
            } else {
                project.repoMetrics.filter {
                    filterRepo(repoTypeList, repoName, it)
                }.forEach { repo ->
                    when (metrics) {
                        Metrics.PROJECTNODESIZE -> {
                            val current = tmpMap[project.projectId] ?: 0
                            tmpMap[project.projectId] = current + repo.size
                        }
                        Metrics.PROJECTNODENUM -> {
                            val current = tmpMap[project.projectId] ?: 0
                            tmpMap[project.projectId] = current + repo.num
                        }
                        else -> {}
                    }
                }
            }
        }
        return tmpMap
    }

    private fun filterRepo(repoTypeList: List<String>?, repoName: String?, repoMetrics: RepoMetrics): Boolean {
        return if (repoTypeList.isNullOrEmpty()) {
            if (repoName.isNullOrEmpty()) {
                true
            } else {
                repoMetrics.repoName == repoName
            }
        } else {
            if (repoName.isNullOrEmpty()) {
                repoMetrics.type in repoTypeList
            } else {
                repoMetrics.type in repoTypeList && repoMetrics.repoName == repoName
            }
        }
    }

    private fun getFilterInfo(target: Target): Pair<FilterType, String?> {
        val reqData = if (target.data is Map<*, *>) {
            target.data as Map<String, Any>
        } else {
            null
        }
        val filterType = FilterType.valueOf((reqData?.get(FILTER_TYPE) as? String) ?: FilterType.ALL.name)
        val filterValue = reqData?.get(FILTER_VALUE) as? String
        return Pair(filterType, filterValue)
    }
}