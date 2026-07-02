package com.tencent.bkrepo.common.metadata.service.log.impl

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.log.OperateLogCompensationDao
import com.tencent.bkrepo.common.metadata.dao.log.OperateLogDao
import com.tencent.bkrepo.common.metadata.model.TOperateLog
import com.tencent.bkrepo.common.metadata.model.TOperateLogCompensation
import com.tencent.bkrepo.common.metadata.model.TOperateLogCompensation.Companion.MAX_RETRY
import com.tencent.bkrepo.common.metadata.model.TOperateLogCompensation.Companion.STATUS_DONE
import com.tencent.bkrepo.common.metadata.model.TOperateLogCompensation.Companion.STATUS_FAILED
import com.tencent.bkrepo.common.metadata.model.TOperateLogCompensation.Companion.STATUS_PENDING
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * §20.3.2：oplog 跨实例写入失败时入队，不阻断 node 删除等主路径。
 */
@Component
@Conditional(SyncCondition::class)
class OperateLogCompensationService(
    private val dao: OperateLogCompensationDao,
    private val operateLogDao: OperateLogDao,
    @Autowired(required = false)
    private val routingRegistry: MongoRoutingRegistry? = null,
) {

    fun enabled(): Boolean = routingRegistry?.let { registry ->
        registry.isRoutingEnabled(OPLOG_RULE) || registry.isRoutingEnabled(NODE_RULE)
    } == true

    fun enqueue(log: TOperateLog, error: Throwable? = null) {
        if (!enabled()) return
        dao.save(
            TOperateLogCompensation(
                log = log,
                lastError = error?.message,
            ),
        )
    }

    @Scheduled(
        fixedDelayString = "\${spring.data.mongodb.multi-instance.compensation.oplog.interval-ms:1000}",
        initialDelayString = "\${spring.data.mongodb.multi-instance.compensation.oplog.interval-ms:1000}",
    )
    fun consume() {
        if (!enabled()) return
        val tasks = dao.findPending(BATCH_SIZE)
        if (tasks.isEmpty()) return
        for (task in tasks) {
            processSingle(task)
        }
    }

    private fun processSingle(task: TOperateLogCompensation) {
        val id = task.id ?: return
        try {
            operateLogDao.insert(task.log)
            dao.updateFirst(
                Query(Criteria.where("_id").`is`(id)),
                Update().set(TOperateLogCompensation::status.name, STATUS_DONE),
            )
        } catch (e: Exception) {
            val newRetry = task.retryCount + 1
            if (newRetry >= MAX_RETRY) {
                logger.error(
                    "artifact_oplog compensation failed after $MAX_RETRY retries, " +
                        "projectId=${task.log.projectId} type=${task.log.type}",
                    e,
                )
                dao.updateFirst(
                    Query(Criteria.where("_id").`is`(id)),
                    Update().set(TOperateLogCompensation::status.name, STATUS_FAILED)
                        .set(TOperateLogCompensation::retryCount.name, newRetry)
                        .set(TOperateLogCompensation::lastError.name, e.message),
                )
            } else {
                logger.warn(
                    "artifact_oplog compensation retry $newRetry/${MAX_RETRY - 1}, " +
                        "projectId=${task.log.projectId}: ${e.message}",
                )
                dao.updateFirst(
                    Query(Criteria.where("_id").`is`(id)),
                    Update().set(TOperateLogCompensation::status.name, STATUS_PENDING)
                        .set(TOperateLogCompensation::retryCount.name, newRetry)
                        .set(TOperateLogCompensation::lastError.name, e.message),
                )
            }
        }
    }

    companion object {
        private const val OPLOG_RULE = "artifact-oplog"
        private const val NODE_RULE = "node"
        private const val BATCH_SIZE = 100
        private val logger = LoggerFactory.getLogger(OperateLogCompensationService::class.java)
    }
}
