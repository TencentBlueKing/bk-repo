/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.exception

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 通用文件错误码
 */
enum class ReplicationMessageCode(private val businessCode: Int, private val key: String) : MessageCode {
    REMOTE_CLUSTER_CONNECT_ERROR(1, "remote.cluster.connect.error"),
    REMOTE_CLUSTER_SSL_ERROR(2, "remote.cluster.ssl.error"),
    TASK_STATUS_INVALID(3, "task.status.invalid"),
    TASK_ENABLED_FALSE(4, "task.enabled.false"),
    CLUSTER_NODE_EXISTS(5, "cluster.node.existed"),
    CLUSTER_NODE_NOT_FOUND(6, "cluster.node.notfound"),
    SCHEDULED_JOB_LOADING(7, "schedule.job.loading"),
    TASK_DISABLE_UPDATE(8, "task.disable.update"),
    CLUSTER_CENTER_NODE_EXISTS(9, "cluster.center.node.existed"),
    REPLICA_TASK_NOT_FOUND(10, "replica.task.notfound"),
    REPLICA_ARTIFACT_BROKEN(11, "replica.artifact.broken"),
    REPLICA_TASK_TIMEOUT(12, "replica.task.timeout"),
    REPLICA_CLUSTER_NOT_FOUND(13, "replica.cluster.not-found"),
    REPLICA_TASK_FAILED(14, "replica.task.failed"),
    REPLICA_NODE_DISPATCH_CONFIG_NOT_FOUND(15, "replica.node.dispatch.config.notfound"),
    UNSUPPORTED_CLUSTER_VERSION(16, "unsupported.cluster.version"),
    FEDERATION_REPOSITORY_CREATE_ERROR(17, "federation.repository.create.error"),

    ;

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 3
}
