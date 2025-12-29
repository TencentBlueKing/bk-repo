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


}
