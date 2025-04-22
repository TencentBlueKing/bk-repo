package com.tencent.bkrepo.common.service.log

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/service/logs")
class LogDataController(private val logDataService: LogDataService) {

    @GetMapping("/data")
    fun data(
        @RequestParam(required = true) logType: LogType,
        @RequestParam(required = true) startPosition: Long,
        @RequestParam(required = true) maxSize: Long
    ): Response<LogData> {
        return ResponseBuilder.success(logDataService.getLogData(logType, startPosition, maxSize))
    }
}