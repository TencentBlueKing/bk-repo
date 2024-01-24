/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.COUNT
import com.tencent.bkrepo.job.CREDENTIALS
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.FileJobContext
import com.tencent.bkrepo.job.config.properties.FileReferenceCleanupJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 清理引用=0的文件
 */
@Component
@EnableConfigurationProperties(FileReferenceCleanupJobProperties::class)
class FileReferenceCleanupJob(
    private val storageService: StorageService,
    private val storageCredentialsClient: StorageCredentialsClient,
    properties: FileReferenceCleanupJobProperties,
    private val archiveClient: ArchiveClient,
) : MongoDbBatchJob<FileReferenceCleanupJob.FileReferenceData, FileJobContext>(properties) {

    override fun start(): Boolean {
        return super.start()
    }

    override fun createJobContext(): FileJobContext {
        return FileJobContext()
    }

    override fun entityClass(): KClass<FileReferenceData> {
        return FileReferenceData::class
    }

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT)
            .map { "$COLLECTION_NAME_PREFIX$it" }
            .toList()
    }

    override fun buildQuery(): Query {
        return Query(Criteria.where(COUNT).isEqualTo(0))
    }

    override fun run(row: FileReferenceData, collectionName: String, context: FileJobContext) {
        val credentialsKey = row.credentialsKey
        val sha256 = row.sha256
        val id = row.id
        val storageCredentials = credentialsKey?.let { getCredentials(credentialsKey) }
        try {
            if (sha256.isNotBlank() && storageService.exist(sha256, storageCredentials)) {
                if (existNode(sha256)) {
                    return
                }
                storageService.delete(sha256, storageCredentials)
            } else {
                context.fileMissing.incrementAndGet()
                logger.warn("File[$sha256] is missing on [$storageCredentials], skip cleaning up.")
            }
            cleanupRelatedResources(sha256, credentialsKey)
            mongoTemplate.remove(Query(Criteria(ID).isEqualTo(id)), collectionName)
        } catch (e: Exception) {
            throw JobExecuteException("Failed to delete file[$sha256] on [$storageCredentials].", e)
        }
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(14)

    /**
     * 检查Node表中是否还存在对应sha256的node
     */
    private fun existNode(sha256: String): Boolean {
        (0 until SHARDING_COUNT).forEach {
            val query = Query(where(Node::sha256).isEqualTo(sha256))
            val exist = mongoTemplate.findOne(query, Node::class.java, COLLECTION_NODE_PREFIX + it) != null
            if (exist) {
                logger.info("sha256[$sha256] still has existed node in collection[$it]")
                return true
            }
        }
        return false
    }

    private fun getCredentials(key: String): StorageCredentials? {
        return cacheMap.getOrPut(key) {
            storageCredentialsClient.findByKey(key).data ?: return null
        }
    }

    private fun cleanupRelatedResources(sha256: String, credentialsKey: String?) {
        val criteria = Criteria.where(SHA256).isEqualTo(sha256)
            .and(STORAGE_CREDENTIALS).isEqualTo(credentialsKey)
        val query = Query(criteria)
        mongoTemplate.findOne(query, Node::class.java, COMPRESS_FILE_COLLECTION)?.let {
            val deleteCompressFileRequest = DeleteCompressRequest(sha256, credentialsKey, SYSTEM_USER)
            archiveClient.deleteCompress(deleteCompressFileRequest)
        }
        mongoTemplate.findOne(query, Node::class.java, ARCHIVE_FILE_COLLECTION)?.let {
            val deleteArchiveFileRequest = ArchiveFileRequest(sha256, credentialsKey, SYSTEM_USER)
            archiveClient.delete(deleteArchiveFileRequest)
        }
    }

    private val cacheMap: ConcurrentHashMap<String, StorageCredentials?> = ConcurrentHashMap()

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME_PREFIX = "file_reference_"
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val COMPRESS_FILE_COLLECTION = "compress_file"
        private const val ARCHIVE_FILE_COLLECTION = "archive_file"
        private const val STORAGE_CREDENTIALS = "storageCredentialsKey"
    }

    data class FileReferenceData(private val map: Map<String, Any?>) {
        val id: String? by map
        val sha256: String by map
        val credentialsKey: String? = map[CREDENTIALS] as String?
    }

    data class Node(
        val id: String,
        val sha256: String?,
    )

    override fun mapToEntity(row: Map<String, Any?>): FileReferenceData {
        return FileReferenceData(row)
    }
}
