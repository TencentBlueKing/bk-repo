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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.opdata.constant.OPDATA_CAP_SIZE
import com.tencent.bkrepo.opdata.constant.OPDATA_CUSTOM
import com.tencent.bkrepo.opdata.constant.OPDATA_CUSTOM_NUM
import com.tencent.bkrepo.opdata.constant.OPDATA_CUSTOM_SIZE
import com.tencent.bkrepo.opdata.constant.OPDATA_GRAFANA_NUMBER
import com.tencent.bkrepo.opdata.constant.OPDATA_GRAFANA_STRING
import com.tencent.bkrepo.opdata.constant.OPDATA_NODE_NUM
import com.tencent.bkrepo.opdata.constant.OPDATA_PIPELINE
import com.tencent.bkrepo.opdata.constant.OPDATA_PIPELINE_NUM
import com.tencent.bkrepo.opdata.constant.OPDATA_PIPELINE_SIZE
import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_ID
import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_NUM
import com.tencent.bkrepo.opdata.constant.OPDATA_REPO_NAME
import com.tencent.bkrepo.opdata.constant.OPDATA_STAT_LIMIT
import com.tencent.bkrepo.opdata.constant.PROJECT_NAME
import com.tencent.bkrepo.opdata.model.FileExtensionMetricsModel
import com.tencent.bkrepo.opdata.model.NodeModel
import com.tencent.bkrepo.opdata.model.ProjectMetricsModel
import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.RepoModel
import com.tencent.bkrepo.opdata.model.TFileExtensionMetrics
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.Columns
import com.tencent.bkrepo.opdata.pojo.NodeResult
import com.tencent.bkrepo.opdata.pojo.QueryRequest
import com.tencent.bkrepo.opdata.pojo.QueryResult
import com.tencent.bkrepo.opdata.pojo.SearchRequest
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.FileExtensionMetrics
import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GrafanaService @Autowired constructor(
    private val repoModel: RepoModel,
    private val nodeModel: NodeModel,
    private val projectModel: ProjectModel,
    private val projectMetricsRepository: ProjectMetricsRepository,
    private val projectMetricsModel: ProjectMetricsModel,
    private val fileExtensionMetricsModel: FileExtensionMetricsModel
) {
    fun search(request: SearchRequest): List<String> {
        val data = mutableListOf<String>()
        val target = if (request.target.isBlank()) Metrics.DEFAULT else Metrics.valueOf(request.target.split(":")[0])
        when (target) {
            Metrics.PROJECTIDLIST -> {
                dealProjectIdList(data)
            }
            Metrics.REPONAMELIST -> {
                dealRepoNameList(request.target, data)
            }
            else -> {
                for (metric in Metrics.values()) {
                    data.add(metric.name)
                }
            }
        }
        return data
    }

    fun query(request: QueryRequest): List<Any> {
        val result = mutableListOf<Any>()
        request.targets.forEach {
            when (it.target) {
                Metrics.PROJECTNUM -> {
                    dealProjectNum(it, result)
                }
                Metrics.PROJECLIST -> {
                    dealProjectList(it, result)
                }
                Metrics.REPOLIST -> {
                    dealProjectList(it, result)
                }
                Metrics.PROJECTNODENUM -> {
                    dealProjectNodeNum(result)
                }
                Metrics.PROJECTNODESIZE -> {
                    dealProjectNodeSize(result)
                }
                Metrics.CAPSIZE -> {
                    dealCapSize(it, result)
                }
                Metrics.NODENUM -> {
                    dealNodeNum(it, result)
                }
                Metrics.EFFECTIVEPROJECTNUM -> {
                    dealEffectiveProjectNum(it, result)
                }
                Metrics.NODESIZEDISTRIBUTION -> {
                    dealNodeSizeDistribution(it, result)
                }
                Metrics.FILEEXTENSION -> {
                    dealFileExtension(it, result)
                }
                else -> {
                    dealNodeNum(it, result)
                }
            }
        }
        return result
    }

    private fun dealFileExtension(target: Target, result: MutableList<Any>) {
        val reqData = target.data as Map<String, Any>
        val projectId = reqData[OPDATA_PROJECT_ID] as String?
        val repoName = reqData[OPDATA_REPO_NAME] as String?
        val metric = FileExtensionMetrics.valueOf(reqData[METRICS] as? String ?: FileExtensionMetrics.NUM.name)
        val resultList = if (projectId.isNullOrBlank()) {
            fileExtensionMetricsModel.getFileExtensionMetrics(metric)
        } else if (repoName.isNullOrBlank()) {
            fileExtensionMetricsModel.getProjFileExtensionMetrics(projectId, metric)
        } else {
            fileExtensionMetricsModel.getRepoFileExtensionMetrics(projectId, repoName, metric)
        }
        resultList.forEach {
            val data = listOf(it[metric.name.toLowerCase()] as Long, System.currentTimeMillis())
            val element = listOf(data)
            result.add(NodeResult(it[TFileExtensionMetrics::extension.name] as String, element))
        }
    }

    private fun dealProjectIdList(result: MutableList<String>) {
        result.addAll(projectModel.getProjectList().map { it.name })
    }

    private fun dealRepoNameList(target: String, result: MutableList<String>) {
        val projectId = target.split(":")[1]
        result.addAll(repoModel.getRepoListByProjectId(projectId))
    }

    private fun dealNodeSizeDistribution(target: Target, result: MutableList<Any>) {
        val resultMap = if (target.data.toString().isBlank()) {
            projectMetricsModel.getSizeDistribution()
        } else {
            val data = target.data as Map<String, Any>
            val projectId = data[OPDATA_PROJECT_ID] as String?
            val repoName = data[OPDATA_REPO_NAME] as String?
            if (projectId.isNullOrBlank()) {
                projectMetricsModel.getSizeDistribution()
            } else if (repoName.isNullOrBlank()) {
                projectMetricsModel.getProjSizeDistribution(projectId)
            } else {
                nodeModel.getRepoNodeSizeDistribution(projectId, repoName)
            }
        }
        val results = resultMap.toList().map { Pair(it.first.toLong(), it.second) }.sortedBy { it.first }
        for (i in results.indices) {
            val size = if (i + 1 < results.size) {
                "${HumanReadable.size(results[i].first)} - ${HumanReadable.size(results[i + 1].first)}"
            } else {
                "> ${HumanReadable.size(results[i].first)}"
            }
            val data = listOf(results[i].second, System.currentTimeMillis())
            val element = listOf(data)
            result.add(NodeResult(size, element))
        }
    }

    private fun dealEffectiveProjectNum(target: Target, result: MutableList<Any>) {
        val projects = projectMetricsRepository.findAll()
        val count = projects.filter { it.capSize > 0 }.size.toLong()
        val columns = Columns(OPDATA_PROJECT_NUM, OPDATA_GRAFANA_NUMBER)
        val row = listOf(count)
        val data = QueryResult(listOf(columns), listOf(row), target.type)
        result.add(data)
    }

    private fun dealProjectNum(target: Target, result: MutableList<Any>) {
        val count = projectModel.getProjectNum()
        val column = Columns(OPDATA_PROJECT_NUM, OPDATA_GRAFANA_NUMBER)
        val row = listOf(count)
        val data = QueryResult(listOf(column), listOf(row), target.type)
        result.add(data)
    }

    private fun dealCapSize(target: Target, result: MutableList<Any>) {
        var size = 0L
        val projects = projectMetricsRepository.findAll()
        projects.forEach {
            size += it.capSize
        }
        val column = Columns(OPDATA_CAP_SIZE, OPDATA_GRAFANA_NUMBER)
        val row = listOf(size)
        val data = QueryResult(listOf(column), listOf(row), target.type)
        result.add(data)
    }

    private fun dealNodeNum(target: Target, result: MutableList<Any>) {
        var num = 0L
        val projects = projectMetricsRepository.findAll()
        projects.forEach {
            num += it.nodeNum
        }
        val column = Columns(OPDATA_NODE_NUM, OPDATA_GRAFANA_NUMBER)
        val row = listOf(num)
        val data = QueryResult(listOf(column), listOf(row), target.type)
        result.add(data)
    }

    private fun dealProjectList(target: Target, result: MutableList<Any>) {
        val rows = mutableListOf<List<Any>>()
        val columns = mutableListOf<Columns>()
        val info = projectMetricsRepository.findAll()
        columns.add(Columns(TProjectMetrics::projectId.name, OPDATA_GRAFANA_STRING))
        columns.add(Columns(TProjectMetrics::nodeNum.name, OPDATA_GRAFANA_NUMBER))
        columns.add(Columns(TProjectMetrics::capSize.name, OPDATA_GRAFANA_NUMBER))
        columns.add(Columns(OPDATA_CUSTOM_NUM, OPDATA_GRAFANA_NUMBER))
        columns.add(Columns(OPDATA_CUSTOM_SIZE, OPDATA_GRAFANA_NUMBER))
        columns.add(Columns(OPDATA_PIPELINE_NUM, OPDATA_GRAFANA_NUMBER))
        columns.add(Columns(OPDATA_PIPELINE_SIZE, OPDATA_GRAFANA_NUMBER))
        info.forEach { repo ->
            var customNum = 0L
            var customSize = 0L
            var pipelineNum = 0L
            var pipelineSize = 0L
            repo.repoMetrics.forEach {
                if (it.repoName == OPDATA_CUSTOM) {
                    customNum = it.num
                    customSize = it.size
                }
                if (it.repoName == OPDATA_PIPELINE) {
                    pipelineNum = it.num
                    pipelineSize = it.size
                }
            }
            val row = listOf(
                repo.projectId, repo.nodeNum, repo.capSize,
                customNum, customSize, pipelineNum, pipelineSize
            )
            rows.add(row)
        }
        val data = QueryResult(columns, rows, target.type)
        result.add(data)
    }

    private fun dealProjectNodeSize(result: MutableList<Any>): List<Any> {
        val projects = projectMetricsRepository.findAll()
        val tmpMap = HashMap<String, Long>()
        projects.forEach {
            val projectId = it.projectId
            if (it.capSize != 0L) {
                tmpMap[projectId] = it.capSize
            }
        }
        return convToDisplayData(tmpMap, result)
    }

    private fun dealProjectNodeNum(result: MutableList<Any>): List<Any> {
        val projects = projectMetricsRepository.findAll()
        val tmpMap = HashMap<String, Long>()
        projects.forEach {
            val projectId = it.projectId
            if (it.nodeNum != 0L && projectId != PROJECT_NAME) {
                tmpMap[projectId] = it.nodeNum
            }
        }
        return convToDisplayData(tmpMap, result)
    }

    private fun convToDisplayData(mapData: HashMap<String, Long>, result: MutableList<Any>): List<Any> {
        mapData.toList().sortedByDescending { it.second }.subList(0, OPDATA_STAT_LIMIT).forEach {
            val projectId = it.first
            val data = listOf(it.second, System.currentTimeMillis())
            val element = listOf(data)
            if (it.second != 0L) {
                result.add(NodeResult(projectId, element))
            }
        }
        return result
    }

    companion object {
        private const val METRICS = "metric"
    }
}
