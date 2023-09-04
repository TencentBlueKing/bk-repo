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

package com.tencent.bkrepo.replication.replica.type

import com.google.common.base.Throwables
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationRecord
import com.tencent.bkrepo.replication.pojo.record.ExecutionResult
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.request.RecordDetailInitialRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.setting.ErrorStrategy
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.context.ReplicaExecutionContext
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.util.ReplicationMetricsRecordUtil.convertToReplicationRecordDetailMetricsRecord
import com.tencent.bkrepo.replication.util.ReplicationMetricsRecordUtil.toJson
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * 同步服务抽象类
 * 一次replica执行负责一个任务下的一个集群，在子线程中执行
 */
@Suppress("TooGenericExceptionCaught")
abstract class AbstractReplicaService(
    private val replicaRecordService: ReplicaRecordService,
    private val localDataManager: LocalDataManager
) : ReplicaService {

    /**
     * 同步 task Object
     */
    protected fun replicaTaskObjects(replicaContext: ReplicaContext) {
        with(replicaContext) {
            // 检查版本
            replicator.checkVersion(this)
            if (task.setting.automaticCreateRemoteRepo) {
                // 同步项目信息
                replicator.replicaProject(replicaContext)
                // 同步仓库信息
                replicator.replicaRepo(replicaContext)
            }
            // 按仓库同步
            if (includeAllData(this)) {
                replicaByRepo(this)
                return
            }
            replicaTaskObjectConstraints(this)
        }
    }


    /**
     * 判断是否包含所有仓库数据，进行仓库同步
     */
    open fun includeAllData(context: ReplicaContext): Boolean {
        return false
    }

    /**
     * 同步task object 中的包列表或者paths
     */
    open fun replicaTaskObjectConstraints(replicaContext: ReplicaContext) {
        with(replicaContext) {
            // 按包同步
            taskObject.packageConstraints.orEmpty().forEach {
                replicaByPackageConstraint(this, it)
            }
            // 按路径同步
            taskObject.pathConstraints.orEmpty().forEach {
                replicaByPathConstraint(this, it)
            }
        }
    }


    /**
     * 同步整个仓库数据
     */
    protected fun replicaByRepo(replicaContext: ReplicaContext) {
        val context = initialExecutionContext(replicaContext)
        try {
            if (replicaContext.taskObject.repoType == RepositoryType.GENERIC) {
                // 同步generic节点
                val root = localDataManager.findNodeDetail(
                    projectId = replicaContext.localProjectId,
                    repoName = replicaContext.localRepoName,
                    fullPath = PathUtils.ROOT
                ).nodeInfo
                replicaByPath(context, root)
                logger.info(
                    "replicaByRepo for generic finished" +
                        " ${replicaContext.localProjectId}|${replicaContext.localRepoName}"
                )
                return
            }
            // 同步包
            val option = PackageListOption(pageNumber = 1, pageSize = PAGE_SIZE)
            var packages = localDataManager.listPackagePage(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.localRepoName,
                option = option
            )
            while (packages.isNotEmpty()) {
                packages.forEach {
                    replicaByPackage(context, it)
                }
                option.pageNumber += 1
                packages = localDataManager.listPackagePage(
                    projectId = replicaContext.localProjectId,
                    repoName = replicaContext.localRepoName,
                    option = option
                )
            }
            logger.info("replicaByRepo finished ${replicaContext.localProjectId}|${replicaContext.localRepoName}")
        } catch (throwable: Throwable) {
            setErrorStatus(context, throwable)
            logger.error("replicaByRepo failed,error is ${Throwables.getStackTraceAsString(throwable)}")
        } finally {
            completeRecordDetail(context)
        }
    }

    /**
     * 同步指定包的数据
     */
    protected fun replicaByPackageConstraint(replicaContext: ReplicaContext, constraint: PackageConstraint) {
        val context = initialExecutionContext(replicaContext, packageConstraint = constraint)
        try {
            // 查询本地包信息
            val packageSummary = localDataManager.findPackageByKey(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.taskObject.localRepoName,
                packageKey = constraint.packageKey!!
            )
            replicaByPackage(context, packageSummary, constraint.versions)
        } catch (throwable: Throwable) {
            setErrorStatus(context, throwable)
            setRunOnceTaskFailedRecordMetrics(context, throwable, packageConstraint = constraint)
        } finally {
            completeRecordDetail(context)
        }
    }

    /**
     * 同步指定路径的数据
     */
    protected fun replicaByPathConstraint(replicaContext: ReplicaContext, constraint: PathConstraint) {
        val context = initialExecutionContext(replicaContext, pathConstraint = constraint)
        try {
            val nodeInfo = localDataManager.findNodeDetail(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.localRepoName,
                fullPath = constraint.path!!
            ).nodeInfo
            replicaByPath(context, nodeInfo)
        } catch (throwable: Throwable) {
            logger.error("replicaByPathConstraint ${constraint.path} failed, error is ${throwable.message}")
            setErrorStatus(context, throwable)
            setRunOnceTaskFailedRecordMetrics(context, throwable, pathConstraint = constraint)
        } finally {
            completeRecordDetail(context)
        }
    }

    /**
     * 同步路径
     * 采用广度优先遍历
     */
    private fun replicaByPath(context: ReplicaExecutionContext, node: NodeInfo) {
        with(context) {
            if (!node.folder) {
                replicaFile(context, node)
                return
            }
            // 查询子节点
            localDataManager.listNode(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.localRepoName,
                fullPath = node.fullPath
            ).forEach {
                replicaByPath(this, it)
            }
        }
    }

    /**
     * 同步节点
     */
    private fun replicaFile(context: ReplicaExecutionContext, node: NodeInfo) {
        with(context) {
            val record = ReplicationRecord(
                path = node.fullPath,
                sha256 = node.sha256,
                size = node.size.toString()
            )
            runActionAndPrintLog(context, record) { replicaContext.replicator.replicaFile(replicaContext, node) }
        }
    }

    /**
     * 根据[packageSummary]和版本列表[versionNames]执行同步
     */
    private fun replicaByPackage(
        context: ReplicaExecutionContext,
        packageSummary: PackageSummary,
        versionNames: List<String>? = null
    ) {
        with(context) {
            replicator.replicaPackage(replicaContext, packageSummary)
            // 同步package功能： 对应内部集群配置是当version不存在时则同步全部的package version
            // 而对于外部集群配置而言，当version不存在时，则不进行同步
            val versions = versionNames?.map {
                localDataManager.findPackageVersion(
                    projectId = replicaContext.localProjectId,
                    repoName = replicaContext.localRepoName,
                    packageKey = packageSummary.key,
                    version = it
                )
            } ?: run {
                if (replicaContext.remoteCluster.type == ClusterNodeType.REMOTE) {
                    emptyList()
                } else {
                    localDataManager.listAllVersion(
                        projectId = replicaContext.localProjectId,
                        repoName = replicaContext.localRepoName,
                        packageKey = packageSummary.key,
                        option = VersionListOption()
                    )
                }
            }
            versions.forEach {
                replicaPackageVersion(this, packageSummary, it)
            }
        }
    }

    /**
     * 同步版本
     */
    private fun replicaPackageVersion(
        context: ReplicaExecutionContext,
        packageSummary: PackageSummary,
        version: PackageVersion
    ) {
        with(context) {
            val record = ReplicationRecord(
                packageName = packageSummary.name,
                version = version.name,
                size = version.size.toString()
            )
            runActionAndPrintLog(context, record) {
                replicator.replicaPackageVersion(replicaContext, packageSummary, version)
            }
        }
    }

    private fun runActionAndPrintLog(
        context: ReplicaExecutionContext,
        record: ReplicationRecord,
        action: () -> Boolean
    ) {
        with(context) {
            val startTime = LocalDateTime.now().toString()
            var status: ExecutionStatus = ExecutionStatus.SUCCESS
            var errorReason: String? = null
            try {
                val executed = action()
                updateProgress(executed)
            } catch (throwable: Throwable) {
                status = ExecutionStatus.FAILED
                errorReason = throwable.message.orEmpty()
                logger.error(
                    "replica file failed, " +
                                 "error is ${Throwables.getStackTraceAsString(throwable)}"
                )
                progress.failed += 1
                setErrorStatus(this, throwable)
                if (replicaContext.task.setting.errorStrategy == ErrorStrategy.FAST_FAIL) {
                    throw throwable
                }
            } finally {
                setRunOnceTaskRecordMetrics(
                    task = replicaContext.task,
                    recordId = detail.recordId,
                    startTime = startTime,
                    errorReason = errorReason,
                    status = status,
                    record = record
                )
            }
        }
    }

    /**
     * 记录因需要分发的package或者path本地不存在而导致的异常，分发中的异常已在实际分发处记录
     */
    private fun setRunOnceTaskFailedRecordMetrics(
        context: ReplicaExecutionContext,
        throwable: Throwable,
        packageConstraint: PackageConstraint? = null,
        pathConstraint: PathConstraint? = null
    ) {
        with(context) {
            if (throwable !is IllegalStateException) return
            val record = ReplicationRecord(
                packageName = packageConstraint?.packageKey,
                path = pathConstraint?.path
            )
            setRunOnceTaskRecordMetrics(
                task = replicaContext.task,
                recordId = detail.recordId,
                startTime = LocalDateTime.now().toString(),
                errorReason = throwable.message.orEmpty(),
                status = status,
                record = record
            )
        }
    }

    /**
     * 记录一次性任务执行package或者path分发的执行记录
     */
    private fun setRunOnceTaskRecordMetrics(
        task: ReplicaTaskInfo,
        recordId: String,
        startTime: String,
        status: ExecutionStatus,
        errorReason: String? = null,
        record: ReplicationRecord
    ) {
        logger.info(
            toJson(
                convertToReplicationRecordDetailMetricsRecord(
                    task = task,
                    recordId = recordId,
                    startTime = startTime,
                    status = status,
                    errorReason = errorReason,
                    packageName = record.packageName,
                    version = record.version,
                    path = record.path,
                    sha256 = record.sha256,
                    size = record.size
                )
            )
        )
    }

    /**
     * 初始化执行过程context
     */
    private fun initialExecutionContext(
        context: ReplicaContext,
        packageConstraint: PackageConstraint? = null,
        pathConstraint: PathConstraint? = null
    ): ReplicaExecutionContext {
        // 创建详情
        val request = RecordDetailInitialRequest(
            recordId = context.taskRecord.id,
            remoteCluster = context.remoteCluster.name,
            localRepoName = context.localRepoName,
            repoType = context.localRepoType,
            packageConstraint = packageConstraint,
            pathConstraint = pathConstraint
        )
        val recordDetail = replicaRecordService.initialRecordDetail(request)
        return ReplicaExecutionContext(context, recordDetail)
    }

    /**
     * 设置状态为失败状态
     */
    private fun setErrorStatus(context: ReplicaExecutionContext, throwable: Throwable) {
        context.status = ExecutionStatus.FAILED
        context.appendErrorReason(throwable.message.orEmpty())
        context.replicaContext.status = ExecutionStatus.FAILED
        context.replicaContext.errorMessage = throwable.message.orEmpty()
    }

    /**
     * 持久化同步进度
     */
    private fun completeRecordDetail(context: ReplicaExecutionContext) {
        with(context) {
            val result = ExecutionResult(
                status = status,
                progress = progress,
                errorReason = buildErrorReason()
            )
            replicaRecordService.completeRecordDetail(detail.id, result)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractReplicaService::class.java)
        private const val PAGE_SIZE = 1000
    }
}
