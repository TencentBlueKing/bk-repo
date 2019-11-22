package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.service.MetadataService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

/**
 * 节点自定义查询service
 *
 * @author: carrypan
 * @date: 2019-11-15
 */
@Suppress("UNCHECKED_CAST")
@Service
class NodeQueryService @Autowired constructor(
    private val nodeDao: NodeDao,
    private val nodeQueryBuilder: NodeQueryBuilder,
    private val permissionService: PermissionService
) {

    /**
     * 查询节点
     */
    fun query(queryModel: QueryModel): Page<Map<String, Any>> {
        logger.debug("Node query: [$queryModel]")
        val query = nodeQueryBuilder.build(queryModel)
        return doQuery(query)
    }

    /**
     * 查询节点(提供外部使用，需要鉴权)
     */
    fun userQuery(operator: String, queryModel: QueryModel): Page<Map<String, Any>> {
        logger.debug("User node query: [$queryModel]")
        // 解析projectId和repoName
        val query = nodeQueryBuilder.build(queryModel)
        var projectId: String? = null
        val repoNameList = mutableListOf<String>()
        for (rule in (queryModel.rule as Rule.NestedRule).rules) {
            if (rule is Rule.QueryRule && rule.field == "repoName") {
                when (rule.operation) {
                    OperationType.IN -> (rule.value as List<String>).forEach { repoNameList.add(it) }
                    else -> repoNameList.add(rule.value.toString())
                }
            }
            if (rule is Rule.QueryRule && rule.field == "projectId") {
                projectId = rule.value.toString()
            }
        }
        // 鉴权
        repoNameList.forEach {
            permissionService.checkPermission(CheckPermissionRequest(operator, ResourceType.REPO, PermissionAction.READ, projectId, it))
        }

        return doQuery(query)
    }

    private fun doQuery(query: Query): Page<Map<String, Any>> {
        val nodeList = nodeDao.find(query, MutableMap::class.java) as List<MutableMap<String, Any>>
        // metadata格式转换，并排除id字段
        nodeList.forEach {
            it["metadata"]?.let { metadata -> it["metadata"] = MetadataService.convert(metadata as List<TMetadata>) }
            it.remove("_id")
        }
        val total = nodeDao.count(query)
        return Page((query.skip / query.limit).toInt(), query.limit, total, nodeList)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeQueryService::class.java)
    }
}
