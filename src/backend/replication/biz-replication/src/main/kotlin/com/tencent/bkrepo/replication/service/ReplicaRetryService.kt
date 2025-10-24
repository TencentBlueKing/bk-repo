package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.model.TReplicaFailureRecord

/**
 * 同步重试服务接口
 */
interface ReplicaRetryService {

    /**
     * 重试失败的记录
     * @param failureRecord 失败记录
     * @return 是否重试成功
     */
    fun retryFailureRecord(failureRecord: TReplicaFailureRecord): Boolean

    /**
     * 重试版本分发失败
     * @param failureRecord 失败记录
     * @return 是否重试成功
     */
    fun retryPackageVersionFailure(failureRecord: TReplicaFailureRecord): Boolean

    /**
     * 重试节点分发失败
     * @param failureRecord 失败记录
     * @return 是否重试成功
     */
    fun retryNodeFailure(failureRecord: TReplicaFailureRecord): Boolean
}
