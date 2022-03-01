package com.tencent.bkrepo.job.exception

/**
 * 任务执行异常
 * */
class JobExecuteException(msg: String, cause: Throwable) : RuntimeException(msg, cause)
