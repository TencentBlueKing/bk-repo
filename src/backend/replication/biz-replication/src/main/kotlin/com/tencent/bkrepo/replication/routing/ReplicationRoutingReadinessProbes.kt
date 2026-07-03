package com.tencent.bkrepo.replication.routing

import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessProbe
import org.springframework.stereotype.Component

/** replication 服务本地 P0 就绪探测（A2）。类路径不可达时返回 false。 */
@Component
class ReplicationRoutingReadinessProbe : RoutingReadinessProbe {

    override fun probeId() = "replication"

    override fun checkAll(): List<ReadinessCheckItem> = listOf(
        item("A2", "replication LocalDataManager routing", checkA2()),
    )

    private fun checkA2(): Boolean =
        hasField(A2_CLASS, A2_FIELD)

    companion object {
        private const val A2_CLASS = "com.tencent.bkrepo.replication.manager.LocalDataManager"
        private const val A2_FIELD = "nodeShardReadSupport"

        private fun hasField(className: String, fieldName: String): Boolean =
            runCatching {
                Class.forName(className).declaredFields.any { it.name == fieldName }
            }.getOrDefault(false)

        private fun item(id: String, desc: String, passed: Boolean) =
            ReadinessCheckItem(id = id, description = desc, passed = passed)
    }
}