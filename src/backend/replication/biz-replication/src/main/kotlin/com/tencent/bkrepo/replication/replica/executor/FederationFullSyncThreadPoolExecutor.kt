package com.tencent.bkrepo.replication.replica.executor

import java.util.concurrent.ThreadPoolExecutor

/**
 * 用于联邦仓库全量执行分发到对应执行器的线程池
 */
object FederationFullSyncThreadPoolExecutor {
    /**
     * 线程池实例
     */
    val instance: ThreadPoolExecutor = ThreadPoolExecutorBuilder.build(
        ThreadPoolExecutorBuilder.Configs.FEDERATION_FULL_SYNC
    )
}