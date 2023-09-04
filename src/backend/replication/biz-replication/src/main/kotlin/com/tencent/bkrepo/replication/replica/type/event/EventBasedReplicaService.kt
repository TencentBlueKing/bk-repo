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

package com.tencent.bkrepo.replication.replica.type.event

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.replication.constant.DELAY_IN_SECONDS
import com.tencent.bkrepo.replication.constant.RETRY_COUNT
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.replica.type.AbstractReplicaService
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import org.springframework.stereotype.Component

/**
 * 基于事件消息的实时任务同步器
 */
@Component
class EventBasedReplicaService(
    replicaRecordService: ReplicaRecordService,
    localDataManager: LocalDataManager
) : AbstractReplicaService(replicaRecordService, localDataManager) {

    override fun replica(context: ReplicaContext) {
        with(context) {
            // 同步仓库
            retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) {
                if (task.setting.automaticCreateRemoteRepo) {
                    replicator.replicaRepo(this)
                }
            }
            when (event.type) {
                EventType.NODE_CREATED -> {
                    // 只有非third party集群支持该消息
                    if (context.remoteCluster.type == ClusterNodeType.REMOTE)
                        throw UnsupportedOperationException()
                    val pathConstraint = PathConstraint(event.resourceKey)
                    replicaByPathConstraint(this, pathConstraint)
                }
                EventType.VERSION_CREATED -> {
                    val packageKey = event.data["packageKey"].toString()
                    val packageVersion = event.data["packageVersion"].toString()
                    val packageConstraint = PackageConstraint(packageKey, listOf(packageVersion))
                    replicaByPackageConstraint(this, packageConstraint)
                }
                EventType.VERSION_UPDATED -> {
                    val packageKey = event.data["packageKey"].toString()
                    val packageVersion = event.data["packageVersion"].toString()
                    val packageConstraint = PackageConstraint(packageKey, listOf(packageVersion))
                    replicaByPackageConstraint(this, packageConstraint)
                }
                else -> throw UnsupportedOperationException()
            }
        }
    }
}
