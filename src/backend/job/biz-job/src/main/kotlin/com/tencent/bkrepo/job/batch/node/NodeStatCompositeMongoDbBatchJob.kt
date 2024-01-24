package com.tencent.bkrepo.job.batch.node

import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.ChildMongoDbBatchJob
import com.tencent.bkrepo.job.batch.base.CompositeMongoDbBatchJob
import com.tencent.bkrepo.job.config.properties.NodeStatCompositeMongoDbBatchJobProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import kotlin.reflect.KClass

@Component
@EnableConfigurationProperties(NodeStatCompositeMongoDbBatchJobProperties::class)
class NodeStatCompositeMongoDbBatchJob(
    val properties: NodeStatCompositeMongoDbBatchJobProperties,
    private val redisTemplate: RedisTemplate<String, String>,
    private val activeProjectService: ActiveProjectService
) : CompositeMongoDbBatchJob<NodeStatCompositeMongoDbBatchJob.Node>(properties) {

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "node_$it" }.toList()
    }

    override fun buildQuery(): Query = Query()

    override fun mapToEntity(row: Map<String, Any?>): Node = Node(row)

    override fun entityClass(): KClass<Node> = Node::class

    override fun createChildJobs(): List<ChildMongoDbBatchJob<Node>> {
        return listOf(
            FolderStatChildJob(properties, redisTemplate, mongoTemplate, activeProjectService)
        )
    }

    /**
     * 最长加锁时间
     */
    override fun getLockAtMostFor(): Duration = Duration.ofDays(14)

    data class Node(private val map: Map<String, Any?>) {
        // 需要通过@JvmField注解将Kotlin backing-field直接作为Java field使用，MongoDbBatchJob中才能解析出需要查询的字段
        @JvmField
        val id: String

        @JvmField
        val folder: Boolean

        @JvmField
        val path: String

        @JvmField
        val fullPath: String

        @JvmField
        val name: String

        @JvmField
        val size: Long

        @JvmField
        val deleted: Date?

        @JvmField
        val projectId: String

        @JvmField
        val repoName: String

        init {
            id = map[Node::id.name] as String
            folder = map[Node::folder.name] as Boolean
            path = map[Node::path.name] as String
            fullPath = map[Node::fullPath.name] as String
            name = map[Node::name.name] as String
            size = map[Node::size.name].toString().toLong()
            // 查询出的deleted默认为Date类型
            deleted = map[Node::deleted.name] as Date?
            projectId = map[Node::projectId.name] as String
            repoName = map[Node::repoName.name] as String
        }
    }
}
