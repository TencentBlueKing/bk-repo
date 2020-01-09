package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.opdata.model.NodeModel
import com.tencent.bkrepo.opdata.model.ProjectModel
import com.tencent.bkrepo.opdata.model.RepoModel
import com.tencent.bkrepo.opdata.pojo.Columns
import com.tencent.bkrepo.opdata.pojo.NodeResult
import com.tencent.bkrepo.opdata.pojo.QueryRequest
import com.tencent.bkrepo.opdata.pojo.QueryResult
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GrafanaService @Autowired constructor(
    private val projectModel: ProjectModel,
    private val repoModel: RepoModel,
    private val nodeModel: NodeModel
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

    private fun dealProjectList(target: Target, result: MutableList<Any>) {
        var rows = mutableListOf<List<Any>>()
        var columns = mutableListOf<Columns>()
        val info = projectModel.getProjectList()
        columns.add(Columns(ProjectInfo::name.name, "string"))
        columns.add(Columns(ProjectInfo::displayName.name, "string"))
        columns.add(Columns(ProjectInfo::description.name, "string"))
        info.forEach {
            val row = listOf(it.name, it.displayName, it.description)
            rows.add(row)
        }
        val data = QueryResult(columns, rows, target.type)
        result.add(data)
    }

    private fun dealProjectNodeSize(result: MutableList<Any>): List<Any> {
        val projects = projectModel.getProjectList()
        projects.forEach {
            var totalSize = 0L
            val projectId = it.name
            val repos = repoModel.getRepoListByProjectId(it.name)
            repos.forEach {
                val size = nodeModel.getNodeSize(projectId, it.name)
                totalSize += size
            }
            val displaySize = totalSize / (1024 * 1024 * 1024)
            val data = listOf<Long>(displaySize, System.currentTimeMillis())
            val element = listOf<List<Long>>(data)
            result.add(NodeResult(projectId, element))
        }
        return result
    }

    private fun dealProjectNodeNum(result: MutableList<Any>): List<Any> {
        val projects = projectModel.getProjectList()
        projects.forEach {
            var totalSize = 0L
            val projectId = it.name
            val repos = repoModel.getRepoListByProjectId(it.name)
            repos.forEach {
                val size = nodeModel.getNodeNum(projectId, it.name)
                totalSize += size
            }
            val data = listOf<Long>(totalSize, System.currentTimeMillis())
            val element = listOf<List<Long>>(data)
            result.add(NodeResult(projectId, element))
        }
        return result
    }
}
