package com.tencent.bkrepo.common.artifact.logs

interface LogDataService {
    fun getLogData(logType: LogType, startPosition: Long, maxSize: Long): LogData
}