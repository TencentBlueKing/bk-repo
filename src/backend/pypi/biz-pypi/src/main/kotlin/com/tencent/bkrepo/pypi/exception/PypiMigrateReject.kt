package com.tencent.bkrepo.pypi.exception

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor

class PypiMigrateReject : RejectedExecutionHandler {
    /**
     * Method that may be invoked by a [ThreadPoolExecutor] when
     * [execute][ThreadPoolExecutor.execute] cannot accept a
     * task.  This may occur when no more threads or queue slots are
     * available because their bounds would be exceeded, or upon
     * shutdown of the Executor.
     *
     *
     * In the absence of other alternatives, the method may throw
     * an unchecked [RejectedExecutionException], which will be
     * propagated to the caller of `execute`.
     *
     * @param r the runnable task requested to be executed
     * @param executor the executor attempting to execute this task
     * @throws RejectedExecutionException if there is no remedy
     */
    override fun rejectedExecution(r: Runnable?, executor: ThreadPoolExecutor?) {
        logger.warn("$r is drop")
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PypiMigrateReject::class.java)
    }
}
