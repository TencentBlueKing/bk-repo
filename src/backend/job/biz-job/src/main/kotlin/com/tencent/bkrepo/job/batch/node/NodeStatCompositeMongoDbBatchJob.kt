package com.tencent.bkrepo.job.batch.node

import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.CompositeMongoDbBatchJob
import com.tencent.bkrepo.job.config.properties.StatAllNodeJobProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@EnableConfigurationProperties(StatAllNodeJobProperties::class)
class NodeStatCompositeMongoDbBatchJob(
    properties: StatAllNodeJobProperties
) : CompositeMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "node_$it" }.toList()
    }

    override fun buildQuery(): Query = Query()

    override fun mapToEntity(row: Map<String, Any?>): Node = Node(row)

    override fun entityClass(): Class<Node> = Node::class.java

    override fun createChildJobs(): List<ChildMongoDbBatchJob<Node>> {
        return emptyList()
    }

    class Node(map: Map<String, Any?>) {
        val id: String by map
        val folder: Boolean by map
        val name: String by map
        val size: Long by map
        val deleted: LocalDateTime? by map
        val projectId: String by map
        val repoName: String by map
    }
}
