package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.ilnet.LinkTrafficRequest
import com.tencent.bkrepo.repository.pojo.ilnet.LinkTrafficResponse
import com.tencent.bkrepo.repository.service.ilnet.IlnetLinkTrafficService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Principal(PrincipalType.GENERAL)
@Tag(name = "Link Traffic")
class UserLinkTrafficController(
    private val ilnetLinkTrafficService: IlnetLinkTrafficService,
) {
    @Operation(summary = "查询终端链路拓扑与流量")
    @PostMapping("/api/link/traffic")
    fun queryTraffic(@RequestBody request: LinkTrafficRequest): Response<LinkTrafficResponse> {
        return ResponseBuilder.success(ilnetLinkTrafficService.queryTraffic(request))
    }

    @Operation(summary = "链路流量服务健康检查")
    @GetMapping("/api/link/traffic/health")
    fun health(): Response<Void> {
        ilnetLinkTrafficService.health()
        return ResponseBuilder.success()
    }
}
