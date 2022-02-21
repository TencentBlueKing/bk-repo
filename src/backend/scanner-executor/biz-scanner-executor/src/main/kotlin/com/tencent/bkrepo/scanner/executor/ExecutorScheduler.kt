package com.tencent.bkrepo.scanner.executor

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.scanner.api.ScanClient
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class ExecutorScheduler @Autowired constructor(
    private val scanExecutorFactory: ScanExecutorFactory,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val scanClient: ScanClient,
    private val storageService: StorageService
) {

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun scan() {
        // TODO 添加允许同时执行的扫描任务限制配置
        scanClient.pullSubTask().data?.let { doScan(it) }
    }

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
            scanExecutorFactory.get(subScanTask.scanner.type).scan(executorTask) {
                // TODO 任务上报
                logger.info(it.reportOverview.toString())
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convert(subScanTask: SubScanTask, inputStream: InputStream): ScanExecutorTask<Nothing> {
        with(subScanTask) {
            return ScanExecutorTask(
                taskId = taskId,
                parentTaskId = parentScanTaskId,
                inputStream = inputStream,
                scanner = scanner,
                sha256 = sha256
            ) as ScanExecutorTask<Nothing>
        }
    }

    companion object {
        // TODO 添加到配置文件
        private const val FIXED_DELAY = 3000L
        private val logger = LoggerFactory.getLogger(ExecutorScheduler::class.java)
    }
}
