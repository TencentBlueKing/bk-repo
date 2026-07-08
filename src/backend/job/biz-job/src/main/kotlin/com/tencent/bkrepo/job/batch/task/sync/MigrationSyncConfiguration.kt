package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.metadata.util.BlockNodeCollectionNaming
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate

@Configuration
@ConditionalOnBean(MongoRoutingRegistry::class)
class MigrationSyncConfiguration {

    @Bean
    fun nodeProjectShardMigrationScanStrategy(): MigrationScanStrategy =
        ProjectShardMigrationScanStrategy(
            ruleName = NODE_RULE,
            shardCollectionsProvider = { (0 until NODE_SHARD_COUNT).map { "$NODE_COLLECTION_PREFIX$it" } },
            syncFailedCollection = NODE_SYNC_FAILED_COLLECTION,
        )

    @Bean
    fun blockNodeProjectShardMigrationScanStrategy(
        blockNodeProperties: BlockNodeProperties?,
    ): MigrationScanStrategy = ProjectShardMigrationScanStrategy(
        ruleName = BLOCK_NODE_RULE,
        shardCollectionsProvider = {
            val count = BlockNodeCollectionNaming.shardCount(blockNodeProperties)
            (0 until count).map { BlockNodeCollectionNaming.shardCollection(it, blockNodeProperties) }
        },
        syncFailedCollection = BLOCK_NODE_SYNC_FAILED_COLLECTION,
    )

    @Bean
    fun artifactOplogMigrationScanStrategy(
        @Qualifier("mongoTemplate") defaultMongoTemplate: MongoTemplate,
        properties: MongoMultiInstanceProperties,
    ): MigrationScanStrategy = CollectionFamilyMigrationScanStrategy(
        ruleName = CollectionFamilyMigrationScanStrategy.ARTIFACT_OPLOG_RULE,
        defaultMongoTemplate = defaultMongoTemplate,
        properties = properties,
    )

    @Bean
    fun migrationSyncEngine(
        @Qualifier("mongoTemplate") defaultMongoTemplate: MongoTemplate,
        registry: MongoRoutingRegistry,
        syncStateDao: MigrationSyncStateDao?,
        strategies: List<MigrationScanStrategy>,
    ): MigrationSyncEngine = MigrationSyncEngine(
        defaultMongoTemplate = defaultMongoTemplate,
        registry = registry,
        syncStateDao = syncStateDao,
        strategies = strategies.associateBy { it.ruleName },
    )

    companion object {
        private const val NODE_RULE = "node"
        private const val BLOCK_NODE_RULE = "block-node"
        private const val NODE_COLLECTION_PREFIX = "node_"
        private const val NODE_SHARD_COUNT = 256
        private const val NODE_SYNC_FAILED_COLLECTION = "node_project_sync_failed"
        private const val BLOCK_NODE_SYNC_FAILED_COLLECTION = "block_node_project_sync_failed"
    }
}
