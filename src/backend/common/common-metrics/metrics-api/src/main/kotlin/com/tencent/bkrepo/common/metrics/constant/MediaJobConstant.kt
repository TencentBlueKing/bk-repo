package com.tencent.bkrepo.common.metrics.constant


const val TRANSCODE_JOB_WAITING_COUNT = "transcode.job.waiting.count"
const val TRANSCODE_JOB_WAITING_COUNT_DESC = "转码任务等待数量"
const val TRANSCODE_JOB_QUEUE_COUNT = "transcode.job.queue.count"
const val TRANSCODE_JOB_QUEUE_COUNT_DESC = "转码任务调度中数量"
const val TRANSCODE_JOB_INIT_COUNT = "transcode.job.init.count"
const val TRANSCODE_JOB_INIT_COUNT_DESC = "转码任务创建完成数量"
const val TRANSCODE_JOB_RUNNING_COUNT = "transcode.job.running.count"
const val TRANSCODE_JOB_RUNNING_COUNT_DESC = "转码任务运行中数量"
const val TRANSCODE_JOB_SUCCESS_COUNT = "transcode.job.success.count"
const val TRANSCODE_JOB_SUCCESS_COUNT_DESC = "转码任务成功数量"
const val TRANSCODE_JOB_FAIL_COUNT = "transcode.job.fail.count"
const val TRANSCODE_JOB_FAIL_COUNT_DESC = "转码任务失败数量"
const val TRANSCODE_JOB_DONE_COUNT = "transcode.job.done.count"
const val TRANSCODE_JOB_DONE_COUNT_DESC = "转码任务完成数量（失败后重试成功）"

const val TRANSCODE_JOB_STATUS_CHANGE_COUNT = "transcode.job.status.change.count"
const val TRANSCODE_JOB_STATUS_CHANGE_COUNT_DESC = "转码任务状态变更计数"
const val TRANSCODE_JOB_LOOKBACK_STATUS_COUNT = "transcode.job.lookback.status.count"
const val TRANSCODE_JOB_LOOKBACK_STATUS_COUNT_DESC = "回看日期创建的转码任务各状态数量"
const val TAG_PROJECT_ID = "projectId"
const val TAG_STATUS = "status"
