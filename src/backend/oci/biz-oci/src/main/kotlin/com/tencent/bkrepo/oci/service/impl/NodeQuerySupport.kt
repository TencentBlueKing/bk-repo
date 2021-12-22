package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NodeQuerySupport(
    private val nodeClient: NodeClient
) {
    fun searchNode(
        projectId: String,
        repoName: String,
        sha256: String? = null,
        name: String? = null,
        fullPathPrefix: String? = null
    ): List<Map<String, Any?>> {
        val queryModel = NodeQueryBuilder()
            .select("name", "fullPath")
            .sortByAsc("name")
            .page(1, 10)
            .projectId(projectId)
            .repoName(repoName)
            .excludeFolder()
        sha256?.let { queryModel.and().sha256(it) }
        name?.let { queryModel.and().name(it) }
        fullPathPrefix?.let { queryModel.and().fullPath("/$it", OperationType.PREFIX) }
        val result = nodeClient.search(queryModel.build()).data ?: run {
            logger.warn("node not found in repo [$projectId/$repoName]")
            return emptyList()
        }
        return result.records
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeQuerySupport::class.java)
    }
}
