package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.execution.ExecutionCluster
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.RetryContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.redis.RedisLock
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 推类型分析任务分发器，主动将任务或任务id推到目标集群
 */
abstract class SubtaskPushDispatcher<T : ExecutionCluster>(
    executionCluster: T,
    protected val scannerProperties: ScannerProperties,
    protected val redisOperation: RedisOperation,
    private val scanService: ScanService,
    private val subtaskStateMachine: StateMachine,
    private val temporaryScanTokenService: TemporaryScanTokenService,
    private val executor: ThreadPoolTaskExecutor,
) : AbsSubtaskDispatcher<T>(executionCluster) {

    private val lock = RedisLock(
        redisOperation = redisOperation,
        lockKey = "scanner:dispatcher:lock:${executionCluster.name}",
        expiredTimeInSeconds = DEFAULT_LOCK_SECONDS
    )

    override fun dispatch() {
        if (scanService.peek(executionCluster.name) == null) {
            logger.debug("cluster [${executionCluster.name}] has no subtask to dispatch")
            return
        }

        try {
            if (!lock.tryLock()) {
                logger.info("other process is dispatching to cluster[${executionCluster.name}], skip dispatching")
                return
            }
            val availableCount = availableCount()
            logger.info("cluster [${executionCluster.name}] can execute $availableCount subtasks, starting to dispatch")
            if (availableCount == 0) {
                return
            }
            var dispatchedTaskCount = 0
            for (i in 0 until availableCount) {
                val subtask = scanService.pull(executionCluster.name) ?: break
                dispatchedTaskCount++
                executor.execute { doDispatch(subtask) }
            }
            logger.info("[$dispatchedTaskCount] subtask was dispatched to cluster[${executionCluster.name}]")
        } catch (e: Exception) {
            logger.error("cluster [${executionCluster.name}] dispatch failed", e)
        } finally {
            lock.unlock()
        }
    }

    private fun doDispatch(subtask: SubScanTask) {
        subtask.token = temporaryScanTokenService.createToken(subtask.taskId)
        logger.info("dispatch subtask[${subtask.taskId}] with ${executionCluster.name}")
        if (!dispatch(subtask)) {
            // 分发失败，放回队列中
            logger.error("dispatch subtask failed, ${subtask.trace()}")
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
        private const val DEFAULT_LOCK_SECONDS = 10L * 60L
    }
}
