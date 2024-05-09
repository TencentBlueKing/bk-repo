/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.metrics

const val ARTIFACT_UPLOADING_COUNT = "artifact.uploading.count"
const val ARTIFACT_UPLOADING_COUNT_DESC = "构件实时上传量"
const val ARTIFACT_DOWNLOADING_COUNT = "artifact.downloading.count"
const val ARTIFACT_DOWNLOADING_COUNT_DESC = "构件实时下载量"

const val METER_LIMIT_PREFIX = "artifact.limit."
const val ARTIFACT_UPLOADED_SIZE = "artifact.uploaded.size"
const val ARTIFACT_UPLOADED_SIZE_DESC = "构件已上传量"
const val ARTIFACT_UPLOADING_TIME = "artifact.uploading.time"
const val ARTIFACT_UPLOADING_TIME_DESC = "构件实时上传延迟"
const val ARTIFACT_UPLOADING_SIZE = "artifact.uploading.size"
const val ARTIFACT_UPLOADING_SIZE_DESC = "构件实时上传大小"
const val ARTIFACT_LIMIT_UPLOADING_SIZE = METER_LIMIT_PREFIX + "uploading.size"
const val ARTIFACT_LIMIT_UPLOADING_SIZE_DESC = "构件实时上传大小（包含仓库维度，但限制数量）"
const val ARTIFACT_DOWNLOADED_SIZE = "artifact.downloaded.size"
const val ARTIFACT_DOWNLOADED_SIZE_DESC = "构件已下载量"
const val ARTIFACT_DOWNLOADING_TIME = "artifact.downloading.time"
const val ARTIFACT_DOWNLOADING_TIME_DESC = "构件实时下载延迟"
const val ARTIFACT_DOWNLOADING_SIZE = "artifact.downloading.size"
const val ARTIFACT_DOWNLOADING_SIZE_DESC = "构件实时下载大小"
const val ARTIFACT_LIMIT_DOWNLOADING_SIZE = METER_LIMIT_PREFIX + "downloading.size"
const val ARTIFACT_LIMIT_DOWNLOADING_SIZE_DESC = "构件实时下载大小（包含仓库维度，但限制数量）"
const val ARTIFACT_DOWNLOAD_FAILED_COUNT = "artifact.download.failed.count"
const val ARTIFACT_DOWNLOAD_FAILED_COUNT_DESC = "构件下载失败量"
const val ARTIFACT_ACCESS_TIME = "artifact.access-time"
const val ARTIFACT_ACCESS_TIME_DESC = "构件访问时间"

const val ASYNC_TASK_ACTIVE_COUNT = "async.task.active.count"
const val ASYNC_TASK_ACTIVE_COUNT_DESC = "异步任务实时数量"

const val ASYNC_TASK_QUEUE_SIZE = "async.task.queue.size"
const val ASYNC_TASK_QUEUE_SIZE_DESC = "异步任务队列大小"
