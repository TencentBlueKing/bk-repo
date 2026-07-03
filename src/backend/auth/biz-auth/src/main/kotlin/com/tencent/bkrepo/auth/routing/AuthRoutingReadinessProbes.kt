package com.tencent.bkrepo.auth.routing

import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessProbe
import org.springframework.stereotype.Component

/** auth 服务本地 P0 就绪探测（A1）。类路径不可达时返回 false。 */
@Component
class AuthRoutingReadinessProbe : RoutingReadinessProbe {

    override fun probeId() = "auth"

    override fun checkAll(): List<ReadinessCheckItem> = listOf(
        item("A1", "auth BkiamNodeResourceService routing", checkA1()),
    )

    private fun checkA1(): Boolean =
        hasField(A1_CLASS, A1_FIELD)

    companion object {
        private const val A1_CLASS = "com.tencent.bkrepo.auth.service.bkiamv3.callback.BkiamNodeResourceService"
        private const val A1_FIELD = "nodeShardReadSupport"

        private fun hasField(className: String, fieldName: String): Boolean =
            runCatching {
                Class.forName(className).declaredFields.any { it.name == fieldName }
            }.getOrDefault(false)

        private fun item(id: String, desc: String, passed: Boolean) =
            ReadinessCheckItem(id = id, description = desc, passed = passed)
    }
}