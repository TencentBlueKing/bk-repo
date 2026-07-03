package com.tencent.bkrepo.repository.routing

import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessChecker
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessProbe
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

/**
 * 将 repository 服务的 [RoutingReadinessChecker]（INFRA + M5 + A4/D1/D3）适配为
 * [RoutingReadinessProbe]，供 common-mongo 的统一 Controller 自动收集。
 */
@Component
@ConditionalOnBean(RoutingReadinessChecker::class)
class RepositoryRoutingReadinessProbe(
    private val checker: RoutingReadinessChecker,
) : RoutingReadinessProbe {

    override fun probeId() = "repository"

    override fun checkAll(): List<ReadinessCheckItem> =
        checker.check().checks
}