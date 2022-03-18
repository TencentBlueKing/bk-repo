package com.tencent.bkrepo.scanner.executor

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.scanner.api.ScanClient
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.pojo.request.ReportResultRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

@Component
class ExecutorScheduler @Autowired constructor(
    private val scanExecutorFactory: ScanExecutorFactory,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val scanClient: ScanClient,
    private val storageService: StorageService,
    private val executor: ThreadPoolTaskExecutor
) {

    private val executingCount = AtomicInteger(0)

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun scan() {
        if (allowExecute()) {
            scanClient.pullSubTask().data?.let {
                scanClient.updateSubScanTaskStatus(it.taskId, SubScanTaskStatus.EXECUTING.name)
                executingCount.incrementAndGet()
                executor.execute {
                    try {
                        doScan(it)
                    } finally {
                        executingCount.decrementAndGet()
                    }
                }
                logger.info("executing task count ${executingCount.get()}")
            }
        }
    }

    /**
     * 是否允许执行扫描
     */
    private fun allowExecute(): Boolean {
        return executingCount.get() <= MAX_ALLOW_TASK_COUNT
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun doScan(subScanTask: SubScanTask) {
        val storageCredentials = subScanTask.credentialsKey?.let { storageCredentialsClient.findByKey(it).data!! }
        val artifactInputStream =
            storageService.load(subScanTask.sha256, Range.full(subScanTask.size), storageCredentials)
        if (artifactInputStream == null) {
            logger.warn(
                "Load storage file failed: " +
                        "sha256[${subScanTask.sha256}, credentials: [${subScanTask.credentialsKey}]"
            )
            return
        }

        artifactInputStream.use {
            val executorTask = convert(subScanTask, it)
            scanExecutorFactory.get(subScanTask.scanner.type).scan(executorTask) { result ->
                val request = ReportResultRequest(subScanTask.taskId, subScanTask.parentScanTaskId, result)
                scanClient.report(request)
            }
        }
        logger.info("executing task count ${executingCount.get()}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun convert(subScanTask: SubScanTask, inputStream: InputStream): ScanExecutorTask {
        with(subScanTask) {
            return ScanExecutorTask(
                taskId = taskId,
                parentTaskId = parentScanTaskId,
                inputStream = inputStream,
                scanner = scanner,
                sha256 = sha256
            )
        }
    }

    companion object {
        private const val FIXED_DELAY = 3000L
        private val logger = LoggerFactory.getLogger(ExecutorScheduler::class.java)
        private const val MAX_ALLOW_TASK_COUNT = 4
    }
}
