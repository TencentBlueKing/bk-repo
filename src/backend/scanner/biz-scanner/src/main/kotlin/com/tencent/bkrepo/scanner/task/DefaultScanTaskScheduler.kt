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

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.scanner.dao.SubScanTaskDao
import com.tencent.bkrepo.scanner.model.TSubScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.StorageFile
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.service.ScannerService
import com.tencent.bkrepo.scanner.task.iterator.IteratorManager
import com.tencent.bkrepo.scanner.task.queue.SubScanTaskQueue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
class DefaultScanTaskScheduler @Autowired constructor(
    private val iteratorManager: IteratorManager,
    private val subScanTaskQueue: SubScanTaskQueue,
    private val scannerService: ScannerService,
    private val repositoryClient: RepositoryClient,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val subScanTaskDao: SubScanTaskDao
) : ScanTaskScheduler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun schedule(scanTask: ScanTask) {
        // TODO 实现调度策略
        executor.execute { enqueueAllSubScanTask(scanTask) }
    }

    /**
     * 创建扫描子任务，并提交到扫描队列
     */
    private fun enqueueAllSubScanTask(scanTask: ScanTask) {
        val storageCredentialCache = LRUCache<String, StorageCredentials?>(DEFAULT_STORAGE_CREDENTIALS_CACHE_SIZE)
        val scanner = scannerService.get(scanTask.scanner)
        val nodeIterator = iteratorManager.createNodeIterator(scanTask, false)
        nodeIterator.forEach { node ->
            with(node) {
                val cacheKey = generateKey(projectId, repoName)
                val storageCredentials = storageCredentialCache.getOrPut(cacheKey) {
                    requestStorageCredential(projectId, repoName)
                }
                // TODO 实现批量子任务提交
                val savedSubTask = subScanTaskDao.save(
                    TSubScanTask(
                        createdDate = LocalDateTime.now(),
                        parentScanTaskId = scanTask.taskId
                    )
                )
                val subTask = SubScanTask(
                    savedSubTask.id!!, scanTask.taskId, scanner, StorageFile(node.sha256, storageCredentials)
                )
                subScanTaskQueue.enqueue(subTask)
            }
        }
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

    private fun requestStorageCredential(projectId: String, repoName: String): StorageCredentials? {
        val repoRes = repositoryClient.getRepoInfo(projectId, repoName)
        if (repoRes.isNotOk()) {
            logger.error(
                "Get repo info failed: code[${repoRes.code}], message[${repoRes.message}]," +
                    " projectId[$projectId], repoName[$repoName]"
            )
        }
        val repo = repoRes.data!!
        if (repo.storageCredentialsKey == null) {
            return null
        }
        val storageCredentialsRes = storageCredentialsClient.findByKey(repo.storageCredentialsKey)
        if (storageCredentialsRes.isNotOk()) {
            logger.error("Get storage credential failed: key[${repo.storageCredentialsKey}]")
        }
        return storageCredentialsRes.data
    }

    private fun generateKey(projectId: String, repoName: String) = "prj:$projectId:repo:$repoName"

    companion object {
        private const val DEFAULT_STORAGE_CREDENTIALS_CACHE_SIZE = 4

        // 任务执行线程池
        // TODO 线程池参数调整
        private val executor: ThreadPoolExecutor = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            200, 60, TimeUnit.SECONDS, LinkedBlockingQueue(10000)
        )
    }

    private open class LRUCache<K, V>(
        private val cacheSize: Int,
        loadFactor: Float = DEFAULT_LOAD_FACTOR,
        accessOrder: Boolean = true
    ) : LinkedHashMap<K, V>(cacheSize, loadFactor, accessOrder) {

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
            return size > cacheSize
        }

        companion object {
            const val DEFAULT_LOAD_FACTOR = 0.75f
        }
    }
}
