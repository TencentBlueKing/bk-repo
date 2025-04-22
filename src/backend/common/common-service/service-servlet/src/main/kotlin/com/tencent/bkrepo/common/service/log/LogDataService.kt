package com.tencent.bkrepo.common.service.log

interface LogDataService {
    fun getLogData(logType: LogType, startPosition: Long, maxSize: Long): LogData
}