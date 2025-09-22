package com.tencent.bkrepo.replication.replica.executor

import java.util.concurrent.ThreadPoolExecutor

/**
 * 用于处理联邦仓库文件同步的线程池
 */
object FederationFileThreadPoolExecutor {
    /**
     * 线程池实例
     */
    val instance: ThreadPoolExecutor = ThreadPoolExecutorBuilder.build(
        ThreadPoolExecutorBuilder.Configs.FEDERATION_FILE
    )
}
