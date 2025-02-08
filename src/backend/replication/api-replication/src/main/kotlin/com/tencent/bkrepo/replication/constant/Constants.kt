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

package com.tencent.bkrepo.replication.constant

const val DEFAULT_VERSION = "1.0.0"

const val REPOSITORY = "repository"
const val URL = "url"

const val USERNAME = "username"
const val PASSWORD = "password"
const val CERTIFICATE = "certificate"

const val DOCKER_MANIFEST_JSON_FULL_PATH = "/%s/%s/manifest.json"
const val DOCKER_LAYER_FULL_PATH = "/%s/%s/%s"
const val OCI_MANIFEST_JSON_FULL_PATH = "/%s/manifest/%s/manifest.json"
const val OCI_LAYER_FULL_PATH = "/%s/blobs/%s"
const val OCI_LAYER_FULL_PATH_V1 = "/%s/blobs/%s/%s"
const val BLOB_PATH_REFRESHED_KEY = "blobPathRefreshed"
const val NODE_FULL_PATH = "fullPath"
const val SIZE = "size"
const val REPOSITORY_INFO = "repo"
const val SHA256 = "sha256"
const val MD5 = "md5"
const val FILE = "file"
const val STORAGE_KEY = "storageKey"
const val CHUNKED_UPLOAD = "chunkedUpload"

const val PIPELINE_ID = "pipelineId"
const val BUILD_ID = "buildId"
const val TASK_ID = "taskId"
const val NAME = "name"

const val RETRY_COUNT = 2
const val DELAY_IN_SECONDS: Long = 1

/**
 * metrics
 */
const val REPLICATION_TASK_ACTIVE_COUNT = "replication.task.active.count"
const val REPLICATION_TASK_ACTIVE_COUNT_DESC = "制品同步任务实时执行数量"

const val REPLICATION_TASK_QUEUE_SIZE = "replication.task.queue.size"
const val REPLICATION_TASK_QUEUE_SIZE_DESC = "制品同步任务线程池等待队列大小"

const val REPLICATION_TASK_COMPLETED_COUNT = "replication.task.completed.count"
const val REPLICATION_TASK_COMPLETED_COUNT_DESC = "制品同步已完成的任务数量"

const val OCI_BLOB_UPLOAD_TASK_ACTIVE_COUNT = "oci.blob.upload.task.active.count"
const val OCI_BLOB_UPLOAD_TASK_ACTIVE_COUNT_DESC = "blob上传实时执行数量"

const val OCI_BLOB_UPLOAD_TASK_QUEUE_SIZE = "oci.blob.upload.task.queue.size"
const val OCI_BLOB_UPLOAD_TASK_QUEUE_SIZE_DESC = "blob上传任务线程池等待队列大小"

const val EVENT_CONSUMER_TASK_ACTIVE_COUNT = "event.consumer.task.active.count"
const val EVENT_CONSUMER_TASK_ACTIVE_COUNT_DESC = "事件处理任务实时执行数量"

const val EVENT_CONSUMER_TASK_QUEUE_SIZE = "event.consumer.task.queue.size"
const val EVENT_CONSUMER_TASK_QUEUE_SIZE_DESC = "事件处理任务线程池等待队列大小"

const val RUN_ONCE_EXECUTOR_ACTIVE_COUNT = "runonce.executor.active.count"
const val RUN_ONCE_EXECUTOR_ACTIVE_COUNT_DESC = "执行一次性任务线程池实时执行数量"

const val RUN_ONCE_EXECUTOR_QUEUE_SIZE = "runonce.executor.queue.size"
const val RUN_ONCE_EXECUTOR_QUEUE_SIZE_DESC = "执行一次性任务线程池等待队列大小"

const val MANUAL_TASK_ACTIVE_COUNT = "manual.task.active.count"
const val MANUAL_TASK_ACTIVE_COUNT_DESC = "手动执行具体package或者path分发线程池实时执行数量"

const val MANUAL_TASK_QUEUE_SIZE = "manual.task.queue.size"
const val MANUAL_TASK_QUEUE_SIZE_DESC = "手动执行具体package或者path分发线程池等待队列大小"

const val EDGE_PULL_ACTIVE_COUNT = "edge.pull.task.active.count"
const val EDGE_PULL_ACTIVE_COUNT_DESC = "边缘节点主动拉取任务实时执行数量"
