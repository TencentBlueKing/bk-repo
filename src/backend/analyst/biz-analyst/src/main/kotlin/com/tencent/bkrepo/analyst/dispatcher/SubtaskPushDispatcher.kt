package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.execution.ExecutionCluster
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.RetryContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Semaphore

/**
 * 推类型分析任务分发器，主动将任务或任务id推到目标集群
 */
abstract class SubtaskPushDispatcher<T : ExecutionCluster>(
    executionCluster: T,
    protected val scannerProperties: ScannerProperties,
    private val scanService: ScanService,
    private val subtaskStateMachine: StateMachine,
    private val temporaryScanTokenService: TemporaryScanTokenService,
    private val executor: ThreadPoolTaskExecutor,
) : AbsSubtaskDispatcher<T>(executionCluster) {
    override fun dispatch() {
        if (scanService.peek(executionCluster.name) == null) {
            logger.info("cluster [${executionCluster.name}] has no subtask to dispatch")
            return
        }
        // 不加锁，允许少量超过执行器的资源限制
        val availableCount = availableCount()
        logger.info("cluster [${executionCluster.name}] can execute $availableCount subtasks, starting to dispatch")

        // 通过信号量限制可同时提交的任务数量
        val permit = Semaphore(DEFAULT_PERMITS)
        var dispatchedTaskCount = 0
        for (i in 0 until availableCount) {
            permit.acquire()
            try {
                val subtask = scanService.pull(executionCluster.name) ?: break
                dispatchedTaskCount++
                executor.execute {
                    try {
                        doDispatch(subtask)
                    } finally {
                        permit.release()
                    }
                }
            } catch (e: Exception) {
                permit.release()
                throw e
            }
        }
        logger.info("[$dispatchedTaskCount] subtask was dispatched to cluster[${executionCluster.name}]")
    }

    private fun doDispatch(subtask: SubScanTask) {
        subtask.token = temporaryScanTokenService.createToken(subtask.taskId)
        logger.info("dispatch subtask[${subtask.taskId}] with ${executionCluster.name}")
        if (!dispatch(subtask)) {
            // 分发失败，放回队列中
            logger.warn("dispatch subtask failed, ${subtask.trace()}")
            subtaskStateMachine.sendEvent(
                SubScanTaskStatus.PULLED.name,
                Event(SubtaskEvent.RETRY.name, RetryContext(subtask))
            )
        }
    }

    /**
     * 允许调度多少个任务
     */
    abstract fun availableCount(): Int

    /**
     * 分发任务
     *
     * @param subtask 需要分发的任务
     *
     * @return 是否分发成功
     */
    abstract fun dispatch(subtask: SubScanTask): Boolean

    companion object {
        private val logger = LoggerFactory.getLogger(SubtaskPushDispatcher::class.java)

        /**
         * 默认允许并发提交的任务数
         */
        private const val DEFAULT_PERMITS = 8
    }
}
