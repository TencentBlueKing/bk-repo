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

package com.tencent.bkrepo.job.batch.task.separation

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.PACKAGE_COLLECTION_NAME
import com.tencent.bkrepo.job.PACKAGE_VERSION_COLLECTION_NAME
import com.tencent.bkrepo.job.SEPARATE
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.FixFailedDataSeparationJobProperties
import com.tencent.bkrepo.job.separation.dao.SeparationNodeDao
import com.tencent.bkrepo.job.separation.dao.SeparationPackageDao
import com.tencent.bkrepo.job.separation.dao.SeparationPackageVersionDao
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
import com.tencent.bkrepo.job.separation.exception.SeparationDataNotFoundException
import com.tencent.bkrepo.job.separation.executor.FixFailedRecordTaskExecutor
import com.tencent.bkrepo.job.separation.model.TSeparationFailedRecord
import com.tencent.bkrepo.job.separation.model.TSeparationTask
import com.tencent.bkrepo.job.separation.pojo.NodeFilterInfo
import com.tencent.bkrepo.job.separation.pojo.PackageFilterInfo
import com.tencent.bkrepo.job.separation.pojo.query.NodeBaseInfo
import com.tencent.bkrepo.job.separation.pojo.query.PackageInfo
import com.tencent.bkrepo.job.separation.pojo.query.PackageVersionInfo
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskState
import com.tencent.bkrepo.job.separation.util.SeparationUtils
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 根据配置进行数据降冷
 */
@Component
@EnableConfigurationProperties(FixFailedDataSeparationJobProperties::class)
class FixFailedDataSeparationJob(
    val properties: FixFailedDataSeparationJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val fixFailedRecordTaskExecutor: FixFailedRecordTaskExecutor,
    private val separationTaskDao: SeparationTaskDao,
    private val separationPackageDao: SeparationPackageDao,
    private val separationPackageVersionDao: SeparationPackageVersionDao,
    private val separationNodeDao: SeparationNodeDao,
) : DefaultContextJob(properties) {

    override fun doStart0(jobContext: JobContext) {
        logger.info("start to fix failed separation job")
        val query = Query(
            Criteria.where(TSeparationFailedRecord::triedTimes.name).lt(properties.triedTimes)
        )
        val failedRecords = mongoTemplate.find(
            query, TSeparationFailedRecord::class.java,
            SEPARATION_FAILED_RECORD_COLLECTION_NAME
        )
        failedRecords.forEach {
            try {
                executeFailedRecord(it)
            } catch (e: Exception) {
                logger.error("run fix failed record ${it.id} failed, error: ${e.message}")
            }
        }
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)


    private fun executeFailedRecord(record: TSeparationFailedRecord) {
        val repo = RepositoryCommonUtils.getRepositoryDetail(record.projectId, record.repoName)
        val task = separationTaskDao.findById(record.taskId)
        if (task == null) {
            logger.error("task ${record.taskId} is deleted")
            throw SeparationDataNotFoundException(record.taskId)
        }
        when (repo.type) {
            RepositoryType.GENERIC -> {
                val fullPath = when (task.type) {
                    SEPARATE -> {
                        val nodeQuery = Query(Criteria.where(ID).isEqualTo(record.nodeId))
                        val collectionName = SeparationUtils.getNodeCollectionName(record.projectId)
                        val nodeInfo = mongoTemplate.findOne(nodeQuery, NodeBaseInfo::class.java, collectionName)
                        if (nodeInfo == null) {
                            logger.error("node ${record.nodeId} is deleted")
                            throw SeparationDataNotFoundException(record.id!!)
                        }
                        nodeInfo.fullPath
                    }
                    else -> {
                        val nodeInfo = separationNodeDao.findById(record.nodeId!!, record.actionDate!!)
                        if (nodeInfo == null) {
                            logger.error("cold node ${record.nodeId} is deleted")
                            throw SeparationDataNotFoundException(record.id!!)
                        }
                        nodeInfo.fullPath
                    }
                }
                val content = mutableListOf(NodeFilterInfo(path = fullPath))
                task.content.packages = null
                task.content.paths = content
            }
            else -> {
                val (packageName, version) = when (task.type) {
                    SEPARATE -> {
                        val packageQuery = Query(Criteria.where(ID).isEqualTo(record.packageId))
                        val packageInfo = mongoTemplate.findOne(
                            packageQuery, PackageInfo::class.java, PACKAGE_COLLECTION_NAME
                        )
                        if (packageInfo == null) {
                            logger.error("package ${record.packageId} is deleted")
                            throw SeparationDataNotFoundException(record.id!!)
                        }
                        val versionQuery = Query(Criteria.where(ID).isEqualTo(record.versionId))
                        val versionInfo = mongoTemplate.findOne(
                            versionQuery, PackageVersionInfo::class.java, PACKAGE_VERSION_COLLECTION_NAME
                        )
                        if (versionInfo == null) {
                            logger.error("version ${record.versionId} is deleted")
                            throw SeparationDataNotFoundException(record.id!!)
                        }
                        Pair(packageInfo.name, versionInfo.name)
                    }
                    else -> {
                        val packageInfo = separationPackageDao.findById(record.packageId!!)
                        if (packageInfo == null) {
                            logger.error("the cold package ${record.packageId} is deleted")
                            throw SeparationDataNotFoundException(record.id!!)
                        }
                        val versionInfo = separationPackageVersionDao.findById(record.versionId!!, record.actionDate!!)
                        if (versionInfo == null) {
                            logger.error("cold version ${record.versionId} in ${record.actionDate}  is deleted")
                            throw SeparationDataNotFoundException(record.id!!)
                        }
                        Pair(packageInfo.name, versionInfo.name)
                    }
                }
                val content = mutableListOf(PackageFilterInfo(packageName = packageName, versions = listOf(version)))
                task.content.paths = null
                task.content.packages = content
            }
        }
        val context = buildSeparationContext(task, repo)
        fixFailedRecordTaskExecutor.execute(context)
    }

    private fun buildSeparationContext(
        task: TSeparationTask,
        repo: RepositoryDetail,
    ): SeparationContext {
        return SeparationContext(
            task = task,
            repo = repo,
            fixTask = true
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FixFailedDataSeparationJob::class.java)
        private const val SEPARATION_FAILED_RECORD_COLLECTION_NAME = "separation_failed_record"
        private val STATE_LIST = listOf(SeparationTaskState.PENDING.name, SeparationTaskState.TERMINATED.name)
    }
}
