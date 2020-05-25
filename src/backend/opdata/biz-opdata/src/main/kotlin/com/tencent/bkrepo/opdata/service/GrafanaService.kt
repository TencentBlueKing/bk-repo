package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.Columns
import com.tencent.bkrepo.opdata.pojo.NodeResult
import com.tencent.bkrepo.opdata.pojo.QueryRequest
import com.tencent.bkrepo.opdata.pojo.QueryResult
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GrafanaService @Autowired constructor(
    private val projectModel: ProjectModel,
    private val projectMetricsRepository: ProjectMetricsRepository
) {
    fun search(): List<String> {
        var data = mutableListOf<String>()
        for (metric in Metrics.values()) {
            data.add(metric.name)
        }
        return data
    }

    fun query(request: QueryRequest): List<Any> {
        var result = mutableListOf<Any>()
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
            }
        }
        return result
    }

    private fun dealProjectNum(target: Target, result: MutableList<Any>) {
        val count = projectModel.getProjectNum()
        val column = Columns("ProjectNum", "number")
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
        val column = Columns("CapSize", "number")
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
        val column = Columns("NodeNum", "number")
        val row = listOf(num)
        val data = QueryResult(listOf(column), listOf(row), target.type)
        result.add(data)
    }

    private fun dealProjectList(target: Target, result: MutableList<Any>) {
        var rows = mutableListOf<List<Any>>()
        var columns = mutableListOf<Columns>()
        val info = projectMetricsRepository.findAll()
        columns.add(Columns(TProjectMetrics::projectId.name, "string"))
        columns.add(Columns(TProjectMetrics::nodeNum.name, "number"))
        columns.add(Columns(TProjectMetrics::capSize.name, "number"))
        columns.add(Columns("customNum", "number"))
        columns.add(Columns("customSize", "number"))
        columns.add(Columns("pipelineNum", "number"))
        columns.add(Columns("pipelineSize", "number"))
        info.forEach {
            var customNum = 0L
            var customSize = 0L
            var pipelineNum = 0L
            var pipelineSize = 0L
            it.repoMetrics.forEach {
                if (it.repoName == "custom") {
                    customNum = it.num
                    customSize = it.size
                }
                if (it.repoName == "pipeline") {
                    pipelineNum = it.num
                    pipelineSize = it.size
                }
            }
            val row = listOf(it.projectId, it.nodeNum, it.capSize, customNum, customSize, pipelineNum, pipelineSize)
            rows.add(row)
        }
        val data = QueryResult(columns, rows, target.type)
        result.add(data)
    }

    private fun dealProjectNodeSize(result: MutableList<Any>): List<Any> {
        val projects = projectMetricsRepository.findAll()
        var tmpMap = HashMap<String, Long>()
        projects.forEach {
            val projectId = it.projectId
            if (it.capSize != 0L) {
                tmpMap.put(projectId, it.capSize)
            }
        }
        tmpMap.toList().sortedByDescending { it.second }.subList(0, 29).forEach {
            val projectId = it.first
            val data = listOf<Long>(it.second, System.currentTimeMillis())
            val element = listOf<List<Long>>(data)
            if (it.second != 0L) {
                result.add(NodeResult(projectId, element))
            }
        }
        return result
    }

    private fun dealProjectNodeNum(result: MutableList<Any>): List<Any> {
        val projects = projectMetricsRepository.findAll()
        var tmpMap = HashMap<String, Long>()
        projects.forEach {
            val projectId = it.projectId
            if (it.nodeNum != 0L && projectId != "bkrepo") {
                tmpMap.put(projectId, it.nodeNum)
            }
        }
        tmpMap.toList().sortedByDescending { it.second }.subList(0, 29).forEach {
            val projectId = it.first
            val data = listOf<Long>(it.second, System.currentTimeMillis())
            val element = listOf<List<Long>>(data)
            if (it.second != 0L) {
                result.add(NodeResult(projectId, element))
            }
        }
        return result
    }
}
