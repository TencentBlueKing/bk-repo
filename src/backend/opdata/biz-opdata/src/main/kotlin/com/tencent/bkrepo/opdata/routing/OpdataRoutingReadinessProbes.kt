package com.tencent.bkrepo.opdata.routing

import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessProbe
import org.springframework.stereotype.Component

/** opdata 服务本地 P0 就绪探测（A3）。类路径不可达时返回 false。 */
@Component
class OpdataRoutingReadinessProbe : RoutingReadinessProbe {

    override fun probeId() = "opdata"

    override fun checkAll(): List<ReadinessCheckItem> = listOf(
        item("A3", "opdata GcInfoModel fan-out", checkA3()),
    )

    private fun checkA3(): Boolean =
        hasField(A3_CLASS, A3_FIELD)

    companion object {
        private const val A3_CLASS = "com.tencent.bkrepo.opdata.model.GcInfoModel"
        private const val A3_FIELD = "nodeShardReadSupport"

        private fun hasField(className: String, fieldName: String): Boolean =
            runCatching {
                Class.forName(className).declaredFields.any { it.name == fieldName }
            }.getOrDefault(false)

        private fun item(id: String, desc: String, passed: Boolean) =
            ReadinessCheckItem(id = id, description = desc, passed = passed)
    }
}