package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.repository.constant.SystemMetadata
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * 节点自定义查询服务实现类
 */
@Suppress("UNCHECKED_CAST")
@Service
class NodeQueryServiceImpl(
    private val nodeDao: NodeDao,
    private val nodeQueryInterpreter: NodeQueryInterpreter,
    private val permissionManager: PermissionManager
) : NodeQueryService {

    override fun query(queryModel: QueryModel): Page<Map<String, Any?>> {
        val query = nodeQueryInterpreter.interpret(queryModel)
        return doQuery(query, queryModel)
    }

    override fun userQuery(operator: String, queryModel: QueryModel): Page<Map<String, Any?>> {
        // 解析projectId和repoName
        val query = nodeQueryInterpreter.interpret(queryModel)
        var projectId: String? = null
        val repoNameList = mutableListOf<String>()
        for (rule in (queryModel.rule as Rule.NestedRule).rules) {
            if (rule is Rule.QueryRule && rule.field == REPO_NAME) {
                when (rule.operation) {
                    OperationType.IN -> (rule.value as List<String>).forEach { repoNameList.add(it) }
                    else -> repoNameList.add(rule.value.toString())
                }
            }
            if (rule is Rule.QueryRule && rule.field == PROJECT_ID) {
                projectId = rule.value.toString()
            }
        }
        // 鉴权
        repoNameList.forEach {
            permissionManager.checkPermission(operator, ResourceType.REPO, PermissionAction.READ, projectId!!, it)
        }

        return doQuery(query, queryModel)
    }

    private fun doQuery(query: Query, originalModel: QueryModel): Page<Map<String, Any?>> {
        val nodeList = nodeDao.find(query, MutableMap::class.java) as List<MutableMap<String, Any?>>
        val selectStageTag = originalModel.select.isNullOrEmpty() || originalModel.select!!.contains(NodeInfo::stageTag.name)
        val selectMetadata = originalModel.select.isNullOrEmpty() || originalModel.select!!.contains(NodeInfo::metadata.name)
        // metadata格式转换，并排除id字段
        nodeList.forEach {
            val metadata = it[TNode::metadata.name]?.let { metadata -> convert(metadata as List<Map<String, String>>) }
            if (selectMetadata) {
                it[TNode::metadata.name] = metadata
            }
            if (selectStageTag) {
                it[NodeInfo::stageTag.name] = metadata?.get(SystemMetadata.STAGE.key)
            }
            it[TNode::createdDate.name]?.let { createDate -> it[TNode::createdDate.name] = convertDateTime(createDate) }
            it[TNode::lastModifiedDate.name]?.let { lastModifiedDate -> it[TNode::lastModifiedDate.name] = convertDateTime(lastModifiedDate) }
            it.remove("_id")
        }
        val countQuery = Query.of(query).limit(0).skip(0)
        val totalRecords = nodeDao.count(countQuery)
        val pageNumber = if (query.limit == 0) 0 else (query.skip / query.limit).toInt()

        return Page(pageNumber + 1, query.limit, totalRecords, nodeList)
    }

    companion object {
        fun convert(metadataList: List<Map<String, String>>): Map<String, String> {
            return metadataList.filter { it.containsKey("key") && it.containsKey("value") }
                .map { it.getValue("key") to it.getValue("value") }
                .toMap()
        }

        fun convertDateTime(value: Any): LocalDateTime? {
            return if (value is Date) {
                LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault())
            } else null
        }
    }
}
