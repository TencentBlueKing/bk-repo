package com.tencent.bkrepo.replication.replica.type.federation

import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.record.ReplicaOverview
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.replica.executor.AbstractReplicaJobExecutor
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 联邦全量同步任务执行器
 */
@Component
class FederationFullSyncReplicaJobExecutor(
    clusterNodeService: ClusterNodeService,
    localDataManager: LocalDataManager,
    replicaService: FederationBasedReplicaService,
    replicationProperties: ReplicationProperties,
    private val replicaRecordService: ReplicaRecordService,
) : AbstractReplicaJobExecutor(clusterNodeService, localDataManager, replicaService, replicationProperties) {


    fun execute(taskDetail: ReplicaTaskDetail) {
        logger.info("The federation full sync task[${taskDetail.task.key}] will be manually executed.")
        var replicaOverview: ReplicaOverview? = null
        val taskRecord = replicaRecordService.findOrCreateLatestRecord(taskDetail.task.key)
            .copy(startTime = LocalDateTime.now())
        try {
            val result = taskDetail.task.remoteClusters.map { submit(taskDetail, taskRecord, it) }.map { it.get() }
            replicaOverview = getResultsSummary(result).replicaOverview
            taskRecord.replicaOverview?.let { overview ->
                replicaOverview.success += overview.success
                replicaOverview.failed += overview.failed
                replicaOverview.conflict += overview.conflict
            }
            replicaRecordService.updateRecordReplicaOverview(taskRecord.id, replicaOverview)
        } catch (ignore: Exception) {
            // 记录异常
            logger.error(
                "Federation full sync task[${taskDetail.task.key}]," +
                    " record[${taskRecord.id}] failed: $ignore", ignore
            )
        } finally {
            replicaOverview?.let {
                replicaRecordService.updateRecordReplicaOverview(taskRecord.id, replicaOverview)
            }
            logger.info("Federation full sync task[${taskDetail.task.key}], record[${taskRecord.id}] finished")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationFullSyncReplicaJobExecutor::class.java)
    }
}
