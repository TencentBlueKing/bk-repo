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

/**
 * 推类型分析任务分发器，主动将任务或任务id推到目标集群
 */
abstract class SubtaskPushDispatcher<T: ExecutionCluster>(
    executionCluster: T,
    protected val scannerProperties: ScannerProperties,
    private val scanService: ScanService,
    private val subtaskStateMachine: StateMachine,
    private val temporaryScanTokenService: TemporaryScanTokenService
) : AbsSubtaskDispatcher<T>(executionCluster) {
    override fun dispatch() {
        if (scanService.peek(executionCluster.name) == null) {
            return
        }
        // 不加锁，允许少量超过执行器的资源限制
        val availableCount = availableCount()
        for (i in 0 until availableCount) {
            val subtask = scanService.pull(executionCluster.name) ?: break
            subtask.token = temporaryScanTokenService.createToken(subtask.taskId)
            if (!dispatch(subtask)) {
                // 分发失败，放回队列中
                logger.warn("dispatch subtask failed, subtask[${subtask.taskId}]")
                subtaskStateMachine.sendEvent(
                    SubScanTaskStatus.PULLED.name,
                    Event(SubtaskEvent.RETRY.name, RetryContext(subtask))
                )
            }
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
    }
}
