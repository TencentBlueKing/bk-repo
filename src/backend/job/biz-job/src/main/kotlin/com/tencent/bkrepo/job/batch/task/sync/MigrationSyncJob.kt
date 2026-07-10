package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.job.batch.base.BaseService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled

class MigrationSyncJob(
    @Qualifier("mongoTemplate")
    defaultMongoTemplate: MongoTemplate,
    registry: MongoRoutingRegistry,
    syncStateDao: MigrationSyncStateDao?,
    redisTemplate: RedisTemplate<String, String>,
    lockOperation: LockOperation,
    private val engine: MigrationSyncEngine,
) : BaseService(redisTemplate, lockOperation) {

    @Scheduled(fixedDelayString = "\${bkrepo.mongo.routing.migration-sync.refresh-ms:300000}")
    fun run() {
        val active = engine.loadActiveTasks()
        if (active.isEmpty()) return
        active.forEach { task ->
            refreshData("migration-sync-${task.ruleName}-${task.stateKey}") {
                try {
                    engine.advance(task)
                } catch (e: Exception) {
                    logger.error(
                        "MigrationSyncJob failed for ${task.ruleName}[${task.stateKey}]: ${e.message}", e,
                    )
                    engine.updateState(task, MigrationSyncJobState.INIT_FAILED, error = e.message)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrationSyncJob::class.java)
    }
}
