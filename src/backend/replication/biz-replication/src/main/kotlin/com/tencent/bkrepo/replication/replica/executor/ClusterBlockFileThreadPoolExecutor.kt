package com.tencent.bkrepo.replication.replica.executor

import java.util.concurrent.ThreadPoolExecutor

/**
 * 用于处理集群块文件同步的线程池
 */
object ClusterBlockFileThreadPoolExecutor {
    /**
     * 线程池实例
     */
    val instance: ThreadPoolExecutor = ThreadPoolExecutorBuilder.build(
        ThreadPoolExecutorBuilder.Configs.CLUSTER_BLOCK_FILE
    )
}

