package com.tencent.bkrepo.common.api.thread

import java.util.concurrent.Executor

/**
 * 支持传播的executor包装类
 *
 * 通过这个类执行命令，可以传播threadLocal值
 * */
class TransmitterExecutorWrapper(private val executor: Executor) : Executor {
    override fun execute(command: Runnable) {
        val transmitterRunnableWrapper = TransmitterRunnableWrapper(command)
        executor.execute(transmitterRunnableWrapper)
    }
}
