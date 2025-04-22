package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.log.LogData
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.pojo.log.LogDataConfig
import com.tencent.bkrepo.opdata.service.ServerLogService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@Principal(PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/server/logs")
class ServerLogDataController(
    private val serverLogService: ServerLogService,
) {
    @GetMapping("/config")
    fun config(): Response<LogDataConfig> {
        return ResponseBuilder.success(serverLogService.getServerLogConfig())
    }

    @GetMapping("/data")
    fun data(
        @RequestParam(required = false) nodeId: String? = null,
        @RequestParam(required = true) logFileName: String,
        @RequestParam(required = false) startPosition: Long
    ): Response<LogData> {
        return ResponseBuilder.success(serverLogService.getServerLogData(nodeId, logFileName, startPosition))
    }

}