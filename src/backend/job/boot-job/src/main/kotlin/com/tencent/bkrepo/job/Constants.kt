package com.tencent.bkrepo.job

/**
 * 分表数量
 */
const val SHARDING_COUNT = 256

/**
 * mongodb 最小id
 */
const val MIN_OBJECT_ID = "000000000000000000000000"

/**
 * 一次处理数据量
 */
const val BATCH_SIZE = 1000

/**
 * 数据库字段
 */
const val ID = "_id"
const val SHA256 = "sha256"
const val PROJECT = "projectId"
const val REPO = "repoName"
const val FOLDER = "folder"
const val CREDENTIALS = "credentialsKey"
const val COUNT = "count"

/**
 * metrics
 */
const val ASYNC_TASK_ACTIVE_COUNT = "async.task.active.count"
const val ASYNC_TASK_ACTIVE_COUNT_DESC = "异步任务实时数量"

const val ASYNC_TASK_QUEUE_SIZE = "async.task.queue.size"
const val ASYNC_TASK_QUEUE_SIZE_DESC = "异步任务队列大小"

const val RUNNING_TASK_JOB_COUNT = "running.job.count"
const val RUNNING_TASK_JOB_DESC = "运行中的任务数量"

const val JOB_TASK_COUNT = "job.task.count"
const val JOB_TASK_COUNT_DESC = "任务执行统计"
const val JOB_AVG_TIME_CONSUME = "job.avg.time.consume"
const val JOB_AVG_WAIT_TIME_CONSUME_DESC = "任务平均等待时长统计"
const val JOB_AVG_EXECUTE_TIME_CONSUME_DESC = "任务平均执行时长统计"
