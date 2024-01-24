package com.tencent.bkrepo.job.batch.utils

import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.job.SHARDING_COUNT
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class NodeCommonUtils(
    mongoTemplate: MongoTemplate,
) {
    init {
        Companion.mongoTemplate = mongoTemplate
    }

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val sha256: String,
        val size: Long,
        val lastAccessDate: LocalDateTime? = null,
        val archived: Boolean?,
    )

    companion object {
        lateinit var mongoTemplate: MongoTemplate
        private const val COLLECTION_NAME_PREFIX = "node_"
        fun findNodes(query: Query, storageCredentialsKey: String?): List<Node> {
            val nodes = mutableListOf<Node>()
            (0 until SHARDING_COUNT).map { "$COLLECTION_NAME_PREFIX$it" }.forEach { collection ->
                val find = mongoTemplate.find(query, Node::class.java, collection).filter {
                    val repo = RepositoryCommonUtils.getRepositoryDetail(it.projectId, it.repoName)
                    repo.storageCredentials?.key == storageCredentialsKey
                }
                nodes.addAll(find)
            }
            return nodes
        }

        fun collectionNames(projectIds: List<String>): List<String> {
            val collectionNames = mutableListOf<String>()
            if (projectIds.isNotEmpty()) {
                projectIds.forEach {
                    val index = HashShardingUtils.shardingSequenceFor(it, SHARDING_COUNT)
                    collectionNames.add("${COLLECTION_NAME_PREFIX}$index")
                }
            } else {
                (0 until SHARDING_COUNT).forEach { collectionNames.add("${COLLECTION_NAME_PREFIX}$it") }
            }
            return collectionNames
        }
    }
}
