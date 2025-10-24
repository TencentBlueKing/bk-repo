package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType

/**
 * 同步失败记录服务接口
 */
interface ReplicaFailureRecordService {

    /**
     * 记录分发失败
     */
    fun recordFailure(
        taskKey: String,
        remoteClusterId: String,
        projectId: String,
        localRepoName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        failureType: ReplicaObjectType,
        packageKey: String? = null,
        packageVersion: String? = null,
        fullPath: String? = null,
        failureReason: String? = null
    )

    /**
     * 获取需要重试的记录
     * @param maxRetryTimes 最大重试次数
     * @return 需要重试的记录列表
     */
    fun getRecordsForRetry(maxRetryTimes: Int = 3): List<TReplicaFailureRecord>

    /**
     * 更新重试状态
     * @param recordId 记录ID
     * @param retrying 是否正在重试
     */
    fun updateRetryStatus(recordId: String, retrying: Boolean)

    /**
     * 增加重试次数
     * @param recordId 记录ID
     * @param failureReason 新的失败原因（如果重试失败）
     */
    fun incrementRetryCount(recordId: String, failureReason: String? = null)

    /**
     * 删除失败记录
     * @param recordId 记录ID
     */
    fun deleteRecord(recordId: String)

    /**
     * 清理过期失败记录
     * @param maxRetryNum 最大重试次数
     * @param retentionDays 保留天数
     * @return 被清理的记录数
     */
    fun cleanExpiredRecords(maxRetryNum: Int, retentionDays: Long): Long
}
