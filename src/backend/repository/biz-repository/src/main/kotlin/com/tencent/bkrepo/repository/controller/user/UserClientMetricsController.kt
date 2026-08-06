package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.metrics.ClientPushMetricsRequest
import com.tencent.bkrepo.repository.pojo.metrics.MetricsPushConfigResponse
import com.tencent.bkrepo.repository.service.metrics.ClientMetricsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "客户端指标上报接口")
@RestController
@RequestMapping("/api/client/metrics")
class UserClientMetricsController(
    private val clientMetricsService: ClientMetricsService
) {

    @Principal(PrincipalType.GENERAL)
    @Operation(summary = "上报客户端指标")
    @PostMapping("/push")
    fun pushMetrics(
        @RequestAttribute userId: String,
        @RequestBody request: ClientPushMetricsRequest
    ): Response<MetricsPushConfigResponse> {
        val clientIp = HttpContextHolder.getClientAddress()
        return ResponseBuilder.success(clientMetricsService.pushMetrics(request, clientIp, userId))
    }
}
