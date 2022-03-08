/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.scanner.task

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.scanner.pojo.scanner.Scanner
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.scanner.dao.FileScanResultDao
import com.tencent.bkrepo.scanner.dao.ScanTaskDao
import com.tencent.bkrepo.scanner.dao.SubScanTaskDao
import com.tencent.bkrepo.scanner.model.TSubScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.pojo.SubScanTaskStatus
import com.tencent.bkrepo.scanner.service.ScannerService
import com.tencent.bkrepo.scanner.task.iterator.IteratorManager
import com.tencent.bkrepo.scanner.task.queue.SubScanTaskQueue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Component
class DefaultScanTaskScheduler @Autowired constructor(
    private val iteratorManager: IteratorManager,
    private val subScanTaskQueue: SubScanTaskQueue,
    private val scannerService: ScannerService,
    private val repositoryClient: RepositoryClient,
    private val subScanTaskDao: SubScanTaskDao,
    private val scanTaskDao: ScanTaskDao,
    private val fileScanResultDao: FileScanResultDao,
    private val executor: ThreadPoolTaskExecutor
) : ScanTaskScheduler {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val credentialsCache: LoadingCache<String, String?> = CacheBuilder.newBuilder()
        .maximumSize(DEFAULT_STORAGE_CREDENTIALS_CACHE_SIZE)
        .expireAfterWrite(DEFAULT_STORAGE_CREDENTIALS_CACHE_DURATION_MINUTES, TimeUnit.MINUTES)
        .build(CacheLoader.from { key -> loadStorageCredentialsCache(key!!) })

    @Autowired
    private lateinit var self: DefaultScanTaskScheduler

    override fun schedule(scanTask: ScanTask) {
        executor.execute { enqueueAllSubScanTask(scanTask) }
    }

    /**
     * 创建扫描子任务，并提交到扫描队列
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private fun enqueueAllSubScanTask(scanTask: ScanTask) {
        // 设置扫描任务状态为提交子任务中
        scanTaskDao.updateStatus(scanTask.taskId, ScanTaskStatus.SCANNING_SUBMITTING)
        val scanner = scannerService.get(scanTask.scanner)
        logger.info("submitting sub tasks of task[${scanTask.taskId}], scanner: [${scanner.name}]")

        var submittedSubTaskCount = 0L
        val subScanTasks = ArrayList<TSubScanTask>()
        val nodeIterator = iteratorManager.createNodeIterator(scanTask, false)
        for (node in nodeIterator) {
            val storageCredentialsKey = credentialsCache.get(generateKey(node.projectId, node.repoName))

            // 文件已存在扫描结果，跳过扫描
            if (fileScanResultDao.exists(storageCredentialsKey, node.sha256, scanner.name, scanner.version)) {
                logger.info("skip scan file[${node.sha256}], credentials[$storageCredentialsKey]")
                continue
            }

            subScanTasks.add(
                createSubTask(scanTask, node.sha256, node.size, storageCredentialsKey)
            )

            // 批量提交子任务
            if (subScanTasks.size == BATCH_SIZE || !nodeIterator.hasNext()) {
                submit(subScanTasks, scanner)
                submittedSubTaskCount += subScanTasks.size
                subScanTasks.clear()
            }
        }

        // 更新任务状态为所有子任务已提交
        logger.info("submit $submittedSubTaskCount sub tasks, " +
                "update task[${scanTask.taskId}] status to SCANNING_SUBMITTED")
        scanTaskDao.updateStatus(scanTask.taskId, ScanTaskStatus.SCANNING_SUBMITTED)

        // 没有提交任何子任务，直接设置为任务扫描结束
        if (submittedSubTaskCount == 0L) {
            scanTaskDao.taskFinished(scanTask.taskId)
            logger.info("scan finished, task[${scanTask.taskId}]")
        }
    }

    fun submit(subScanTasks: List<TSubScanTask>, scanner: Scanner) {
        if (subScanTasks.isEmpty()) {
            return
        }
        val subTasks = self.saveSubTasks(subScanTasks).map { convert(it, scanner) }
        logger.info("${subTasks.size} subTasks saved")
        // TODO 实现任务数统计，并发送到influxdb
        subScanTaskQueue.enqueue(subTasks)
        logger.info("${subTasks.size} subTasks enqueued")
        subScanTaskDao.updateStatus(subTasks.map { it.taskId }, SubScanTaskStatus.ENQUEUED)
    }

    fun createSubTask(scanTask: ScanTask, sha256: String, size: Long, credentialKey: String? = null): TSubScanTask {
        val now = LocalDateTime.now()
        return TSubScanTask(
            createdDate = now,
            lastModifiedDate = now,
            parentScanTaskId = scanTask.taskId,
            status = SubScanTaskStatus.CREATED.name,
            scanner = scanTask.scanner,
            sha256 = sha256,
            size = size,
            credentialsKey = credentialKey
        )
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun saveSubTasks(subScanTasks: List<TSubScanTask>): Collection<TSubScanTask> {
        if (subScanTasks.isEmpty()) {
            return emptyList()
        }
        val tasks = subScanTaskDao.insert(subScanTasks)

        // 更新当前正在扫描的任务数
        val task = tasks.first()
        scanTaskDao.updateScanningCount(task.parentScanTaskId, tasks.size)

        return tasks
    }

    override fun resume(scanTask: ScanTask) {
        TODO("Not yet implemented")
    }

    override fun pause(scanTask: ScanTask) {
        TODO("Not yet implemented")
    }

    override fun stop(scanTask: ScanTask) {
        TODO("Not yet implemented")
    }

    private fun loadStorageCredentialsCache(key: String): String? {
        val (projectId, repoName) = fromKey(key)
        val repoRes = repositoryClient.getRepoInfo(projectId, repoName)
        if (repoRes.isNotOk()) {
            logger.error(
                "Get repo info failed: code[${repoRes.code}], message[${repoRes.message}]," +
                        " projectId[$projectId], repoName[$repoName]"
            )
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, repoRes.message ?: "")
        }
        return repoRes.data!!.storageCredentialsKey
    }

    private fun generateKey(projectId: String, repoName: String) = "$projectId$REPO_SPLIT$repoName"
    private fun fromKey(key: String): Pair<String, String> {
        val indexOfRepoSplit = key.indexOf(REPO_SPLIT)
        val projectId = key.substring(0, indexOfRepoSplit)
        val repoName = key.substring(indexOfRepoSplit, key.length)
        return Pair(projectId, repoName)
    }

    private fun convert(subScanTask: TSubScanTask, scanner: Scanner): SubScanTask {
        return SubScanTask(
            taskId = subScanTask.id!!,
            parentScanTaskId = subScanTask.parentScanTaskId,
            scanner = scanner,
            sha256 = subScanTask.sha256,
            size = subScanTask.size,
            credentialsKey = subScanTask.credentialsKey
        )
    }

    companion object {
        private const val REPO_SPLIT = "::repo::"
        private const val DEFAULT_STORAGE_CREDENTIALS_CACHE_SIZE = 1000L
        private const val DEFAULT_STORAGE_CREDENTIALS_CACHE_DURATION_MINUTES = 60L

        /**
         * 批量提交子任务数量
         */
        private const val BATCH_SIZE = 20
    }
}
