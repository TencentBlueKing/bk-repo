/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.job.replicator

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.config.DEFAULT_VERSION
import com.tencent.bkrepo.replication.job.ReplicaContext
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.manager.RemoteDataManager
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

/**
 * 调度类任务同步器
 */
abstract class ScheduledReplicator : Replicator {

    @Value("\${spring.application.version}")
    private var version: String = DEFAULT_VERSION

    @Autowired
    private lateinit var replicaRecordService: ReplicaRecordService

    @Autowired
    protected lateinit var localDataManager: LocalDataManager

    @Autowired
    protected lateinit var remoteDataManager: RemoteDataManager

    override fun replica(context: ReplicaContext) {
        with(context) {
            checkVersion(this)
            // 同步项目
            replicaProject(this)
            // 同步仓库
            replicaRepo(this)
            if (includeAllData(this)) {
                replicaAllData(this)
            } else {
                taskObject.packageConstraints.orEmpty().forEach {
                    replicaByPackageConstraint(this, it)
                }
                taskObject.pathConstraints.orEmpty().forEach {
                    replicaByPathConstraint(this, it)
                }
            }
            completeReplica(this)
        }
    }

    /**
     * 同步项目
     */
    abstract fun replicaProject(context: ReplicaContext)

    /**
     * 同步仓库
     */
    abstract fun replicaRepo(context: ReplicaContext)

    /**
     * 同步包具体逻辑，由子类实现
     */
    abstract fun replicaPackage(context: ReplicaContext)

    /**
     * 同步节点具体逻辑，由子类实现
     */
    abstract fun replicaNode(context: ReplicaContext)

    /**
     * 同步整个仓库数据
     */
    private fun replicaAllData(context: ReplicaContext) {
        with(context) {
            if (taskObject.repoType == RepositoryType.GENERIC) {
                // 同步节点
            } else {
                // 同步包
            }
        }
    }

    /**
     * 同步指定包的数据
     */
    private fun replicaByPackageConstraint(context: ReplicaContext, constraint: PackageConstraint) {
        with(context) {
            // 检查包是否存在
            val packageSummary = packageClient.findPackageByKey(
                projectId = localProjectId,
                repoName = taskObject.localRepoName,
                packageKey = constraint.packageKey
            ).data
            check(packageSummary != null) {
                "Package[$constraint.packageKey] not found in repo[${taskObject.localRepoName}]"
            }
            val versions = if (constraint.versions == null) {
                // 同步所有版本
                // list all version
            } else {
                // 同步指定版本
                packageClient.listAllVersion(
                    projectId = localProjectId,
                    repoName = taskObject.localRepoName,
                    packageKey = constraint.packageKey,
                    option = VersionListOption()
                )
                constraint.versions.orEmpty().forEach {

                }
            }
        }
    }

    /**
     * 同步指定路径的数据
     * 广度优先遍历
     */
    private fun replicaByPathConstraint(context: ReplicaContext, constraint: PathConstraint) {
        // nodeClient.list
        constraint.path
    }

    /**
     * 是否包含所有仓库数据
     */
    private fun includeAllData(context: ReplicaContext): Boolean {
        return context.taskObject.packageConstraints != null &&
            context.taskObject.pathConstraints != null
    }

    /**
     * 校验和远程集群版本是否一致
     */
    protected fun checkVersion(context: ReplicaContext) {
        with(context) {
            val remoteVersion = artifactReplicaClient.version().data.orEmpty()
            if (version != remoteVersion) {
                logger.warn("Local cluster's version[$version] is different from remote cluster[$remoteVersion].")
            }
        }
    }

    /**
     * 持久化同步进度
     */
    protected fun persistProgress(context: ReplicaContext) {
        with(context) {
            replicaRecordService.updateRecordDetailProgress(detailId, progress)
        }
    }

    /**
     * 持久化同步进度
     */
    protected fun completeReplica(context: ReplicaContext) {
        with(context) {
            replicaRecordService.completeRecordDetail(detailId, status = ExecutionStatus.SUCCESS)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduledReplicator::class.java)
    }
}
