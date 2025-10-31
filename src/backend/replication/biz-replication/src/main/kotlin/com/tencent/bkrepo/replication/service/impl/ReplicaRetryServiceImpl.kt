package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaRetryService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 同步重试服务实现
 */
@Service
class ReplicaRetryServiceImpl(
    private val clusterNodeService: ClusterNodeService,
    private val localDataManager: LocalDataManager,
    private val replicaTaskService: ReplicaTaskService,
    private val replicationProperties: ReplicationProperties,
    private val replicaRecordService: ReplicaRecordService,
) : ReplicaRetryService {

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaRetryServiceImpl::class.java)
    }

    override fun retryFailureRecord(failureRecord: TReplicaFailureRecord): Boolean {
        return try {
            when (failureRecord.failureType) {
                ReplicaObjectType.PACKAGE -> retryPackageVersionFailure(failureRecord)
                ReplicaObjectType.PATH -> retryNodeFailure(failureRecord)
                else -> {
                    logger.warn("Unknown failure type: ${failureRecord.failureType}")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to retry failure record[${failureRecord.id}]", e)
            false
        }
    }

    override fun retryPackageVersionFailure(failureRecord: TReplicaFailureRecord): Boolean {
        return try {
            logger.info("Retrying package version failure for record[${failureRecord.id}]")
            val packageSummary = buildPackageSummaryFromFailureRecord(failureRecord)
            val packageVersion = buildPackageVersionFromFailureRecord(failureRecord)
            val context = buildReplicaContextFromFailureRecord(failureRecord) ?: return false
            context.replicator.replicaPackageVersion(context, packageSummary, packageVersion)
        } catch (e: Exception) {
            logger.error("Failed to retry package version failure for record[${failureRecord.id}]", e)
            false
        }
    }

    override fun retryNodeFailure(failureRecord: TReplicaFailureRecord): Boolean {
        return try {
            logger.info("Retrying node failure for record[${failureRecord.id}]")
            val nodeInfo = buildNodeInfoFromFailureRecord(failureRecord)
            val context = buildReplicaContextFromFailureRecord(failureRecord) ?: return false
            context.replicator.replicaFile(context, nodeInfo)
        } catch (e: Exception) {
            logger.error("Failed to retry node failure for record[${failureRecord.id}]", e)
            false
        }
    }

    /**
     * 根据失败记录构建PackageSummary
     */
    private fun buildPackageSummaryFromFailureRecord(failureRecord: TReplicaFailureRecord): PackageSummary {
        with(failureRecord) {
            return localDataManager.findPackageByKey(projectId, repoName, packageKey!!)
        }
    }

    /**
     * 根据失败记录构建PackageVersion
     */
    private fun buildPackageVersionFromFailureRecord(failureRecord: TReplicaFailureRecord): PackageVersion {
        with(failureRecord) {
            return localDataManager.findPackageVersion(projectId, repoName, packageKey!!, packageVersion!!)
        }
    }

    /**
     * 根据失败记录构建NodeInfo
     */
    private fun buildNodeInfoFromFailureRecord(failureRecord: TReplicaFailureRecord): NodeInfo {
        with(failureRecord) {
            return localDataManager.findNodeDetail(projectId, repoName, fullPath!!).nodeInfo
        }
    }


    private fun buildReplicaContextFromFailureRecord(failureRecord: TReplicaFailureRecord): ReplicaContext? {
        with(failureRecord) {
            val taskDetail = replicaTaskService.getDetailByTaskKey(taskKey)

            val clusterNode = clusterNodeService.getByClusterId(remoteClusterId)
                ?: return null
            val localRepo = localDataManager.findRepoByName(projectId, repoName)
            val taskRecord = replicaRecordService.findOrCreateLatestRecord(taskDetail.task.key)
                .copy(startTime = LocalDateTime.now())
            return ReplicaContext(
                taskDetail = taskDetail,
                taskObject = taskDetail.objects.first(),
                taskRecord = taskRecord,
                localRepo = localRepo,
                remoteCluster = clusterNode,
                replicationProperties = replicationProperties
            )
        }
    }
}
