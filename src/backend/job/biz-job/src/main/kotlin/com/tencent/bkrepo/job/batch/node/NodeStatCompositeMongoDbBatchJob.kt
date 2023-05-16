package com.tencent.bkrepo.job.batch.node

import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.CompositeMongoDbBatchJob
import com.tencent.bkrepo.job.config.properties.StatAllNodeJobProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.util.Date

@Component
@EnableConfigurationProperties(StatAllNodeJobProperties::class)
class NodeStatCompositeMongoDbBatchJob(
    private val properties: StatAllNodeJobProperties,
    private val mongoTemplate: MongoTemplate,
) : CompositeMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "node_$it" }.toList()
    }

    override fun buildQuery(): Query = Query()

    override fun mapToEntity(row: Map<String, Any?>): Node = Node(row)

    override fun entityClass(): Class<Node> = Node::class.java

    override fun createChildJobs(): List<ChildMongoDbBatchJob<Node>> {
        return listOf(
            ProjectRepoStatChildJob(properties, mongoTemplate)
        )
    }

    data class Node(private val map: Map<String, Any?>) {
        private val defaultMap = map.withDefault { null }

        val id: String by defaultMap
        val folder: Boolean by defaultMap
        val path: String by defaultMap
        val fullPath: String by defaultMap
        val name: String by defaultMap
        val size: Long by defaultMap
        val deleted: Date? by defaultMap
        val projectId: String by defaultMap
        val repoName: String by defaultMap
    }
}
