/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metrics.constant

const val JOB_ASYNC_TASK_ACTIVE_COUNT = "job.async.task.active.count"
const val JOB_ASYNC_TASK_ACTIVE_COUNT_DESC = "异步任务实时数量"

const val JOB_ASYNC_TASK_QUEUE_SIZE = "job.async.task.queue.size"
const val JOB_ASYNC_TASK_QUEUE_SIZE_DESC = "异步任务队列大小"

const val JOB_BATCH_JOB_ACTIVE_COUNT = "job.batch-job.active.count"
const val JOB_BATCH_JOB_ACTIVE_DESC = "运行中的跑批任务数量"

const val JOB_TASK_COUNT = "job.task.count"
const val JOB_TASK_COUNT_DESC = "任务执行统计"
const val JOB_TIME_CONSUME = "job.task.time"
const val JOB_TIME_CONSUME_DESC = "任务执行时长统计"

const val JOB_TASK_RUNNING_STATUS = "job.running.status"
const val JOB_TASK_RUNNING_STATUS_DESC = "任务执行状态"