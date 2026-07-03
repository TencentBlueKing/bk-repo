package com.tencent.bkrepo.common.service.routing

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessProbe
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessResult
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 统一的本地 P0 就绪检查端点（§17 G-34）。
 *
 * 自动收集所有 [RoutingReadinessProbe] bean，聚合后暴露 GET /routing/readiness。
 * 各服务无需重复编写 Controller，只需注册自己的 Probe @Component 即可。
 *
 * 位于 [common-service] 而非 [common-mongo]：Controller 属于 web 层，与 DB 操作模块分属不同关注点。
 */
@RestController
@RequestMapping("/routing")
@ConditionalOnClass(MongoRoutingRegistry::class)
@ConditionalOnBean(MongoRoutingRegistry::class)
class RoutingReadinessController(
    probes: List<RoutingReadinessProbe>,
) {
    private val probeList = probes

    @GetMapping("/readiness")
    fun readiness(): Response<RoutingReadinessResult> {
        val checks = probeList.flatMap { it.checkAll() }
        return ResponseBuilder.success(
            RoutingReadinessResult(
                ready = checks.all { check -> check.passed },
                checks = checks,
            ),
        )
    }
}