package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.QueryModel
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
    private val nodeQueryInterpreter: NodeQueryInterpreter
) : NodeQueryService {

    override fun query(queryModel: QueryModel): Page<Map<String, Any?>> {
        val context = nodeQueryInterpreter.interpret(queryModel) as NodeQueryContext
        return doQuery(context)
    }

    private fun doQuery(context: NodeQueryContext): Page<Map<String, Any?>> {
        val query = context.mongoQuery
        val nodeList = nodeDao.find(query, MutableMap::class.java) as List<MutableMap<String, Any?>>
        // metadata格式转换，并排除id字段
        nodeList.forEach {
            it.remove("_id")
            it[NodeInfo::createdDate.name]?.let { createDate -> it[TNode::createdDate.name] = convertDateTime(createDate) }
            it[NodeInfo::lastModifiedDate.name]?.let { lastModifiedDate -> it[TNode::lastModifiedDate.name] = convertDateTime(lastModifiedDate) }
            val metadata = it[NodeInfo::metadata.name]?.let { metadata -> convert(metadata as List<Map<String, String>>) }
            if (context.selectMetadata) {
                it[NodeInfo::metadata.name] = metadata
            } else {
                it.remove(NodeInfo::metadata.name)
            }
            if (context.selectStageTag) {
                it[NodeInfo::stageTag.name] = metadata?.get(SystemMetadata.STAGE.key)
            }
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
