package com.tencent.bkrepo.scanner.executor

import com.sun.management.OperatingSystemMXBean
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.scanner.api.ScanClient
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.pojo.request.ReportResultRequest
import org.apache.commons.io.FileUtils
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicInteger

@Component
class ExecutorScheduler @Autowired constructor(
    private val scanExecutorFactory: ScanExecutorFactory,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val scanClient: ScanClient,
    private val storageService: StorageService,
    private val executor: ThreadPoolTaskExecutor,
    private val scannerExecutorProperties: ScannerExecutorProperties
) {

    private val executingCount = AtomicInteger(0)
    private val tika by lazy { Tika() }
    private val operatingSystemBean by lazy { ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean }


    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun scan() {
        while (allowExecute()) {
            val subtask = scanClient.pullSubTask().data ?: break
            scanClient.updateSubScanTaskStatus(subtask.taskId, SubScanTaskStatus.EXECUTING.name)

            executingCount.incrementAndGet()
            logger.info("task start, executing task count ${executingCount.get()}")
            executor.execute {
                val file = File(scannerExecutorProperties.workDir, subtask.sha256)
                try {
                    doScan(subtask, file)
                } finally {
                    file.delete()
                    executingCount.decrementAndGet()
                    logger.info("task finished, executing task count ${executingCount.get()}")
                }
            }
        }
    }

    /**
     * 是否允许执行扫描
     */
    private fun allowExecute(): Boolean {
        return executingCount.get() < operatingSystemBean.totalPhysicalMemorySize / MEMORY_PER_TASK
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun doScan(subScanTask: SubScanTask, file: File) {
        with(subScanTask) {
            val startTimestamp = System.currentTimeMillis()

            // 加载文件
            logger.info("start load file[$sha256]")
            val loadFileSuccess = loadFileTo(subScanTask.credentialsKey, subScanTask.sha256, subScanTask.size, file)
            // 加载文件失败，直接返回
            if (!loadFileSuccess) {
                logger.warn("Load storage file failed: sha256[${sha256}, credentials: [${credentialsKey}]")
                report(taskId, parentScanTaskId, startTimestamp)
                return
            }
            logger.info("load file[$sha256] success, elapse ${System.currentTimeMillis() - startTimestamp}")

            // 执行扫描任务
            logger.info("start to scan file[$sha256]")
            val executorTask = convert(subScanTask, file)
            val executor = scanExecutorFactory.get(subScanTask.scanner.type)
            executor.scan(executorTask) { result ->
                val fileType = tika.detect(file)
                val finishedTimestamp = System.currentTimeMillis()
                val timeSpent = finishedTimestamp - startTimestamp
                logger.info("scan finished[${result.scanStatus}], time spent $timeSpent, reporting result")
                report(taskId, parentScanTaskId, startTimestamp, finishedTimestamp, fileType, result)
            }
        }
    }

    /**
     * 加载文件
     *
     * @param credentialsKey 存储凭证
     * @param sha256 文件sha256
     * @param size 文件大小
     * @param file 文件加载后存储路径
     *
     * @return true 加载成功 false 加载失败
     */
    private fun loadFileTo(credentialsKey: String?, sha256: String, size: Long, file: File): Boolean {
        try {
            val storageCredentials = credentialsKey?.let { storageCredentialsClient.findByKey(it).data!! }
            storageService
                .load(sha256, Range.full(size), storageCredentials)
                ?.use { FileUtils.copyToFile(it, file) }
                ?: return false
            return true
        } catch (ignore: Exception) {
            file.delete()
        }
        return false
    }

    private fun convert(subScanTask: SubScanTask, file: File): ScanExecutorTask {
        with(subScanTask) {
            return ScanExecutorTask(
                taskId = taskId,
                parentTaskId = parentScanTaskId,
                file = file,
                scanner = scanner,
                sha256 = sha256
            )
        }
    }

    private fun report(
        subtaskId: String,
        parentTaskId: String,
        startTimestamp: Long,
        finishedTimestamp: Long = System.currentTimeMillis(),
        fileType: String? = null,
        result: ScanExecutorResult? = null
    ) {
        val request = ReportResultRequest(
            subTaskId = subtaskId,
            parentTaskId = parentTaskId,
            startTimestamp = startTimestamp,
            finishedTimestamp = finishedTimestamp,
            scanStatus = result?.scanStatus ?: SubScanTaskStatus.FAILED.name,
            fileType = fileType,
            scanExecutorResult = result
        )
        scanClient.report(request)
    }

    companion object {
        private const val FIXED_DELAY = 3000L
        private val logger = LoggerFactory.getLogger(ExecutorScheduler::class.java)

        /**
         * 每个任务使用的内存大小未为2GiB
         */
        private const val MEMORY_PER_TASK = 2 * 1024 * 1024 * 1024L
    }
}
