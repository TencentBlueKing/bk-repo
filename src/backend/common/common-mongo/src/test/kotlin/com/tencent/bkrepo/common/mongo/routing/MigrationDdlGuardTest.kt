package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties.RoutingRule
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * M7/G-26：迁移期 DDL 冻结门禁纯单元测试（配置判断，不触真库）。
 * 覆盖 MigrationDdlGuard.assertDdlAllowed 的 4 个分支：
 *   1. 非迁移集合（resolveRuleName=null）→ 放行
 *   2. freezeDdl=false → 放行
 *   3. freezeDdl=true 但 instanceLabel 不在冻结实例列表 → 放行
 *   4. freezeDdl=true 且命中冻结实例 → 抛 IllegalStateException
 */
class MigrationDdlGuardTest {

    private fun ruleWith(freezeDdl: Boolean, freezeDdlInstances: List<String> = emptyList()): RoutingRule {
        return RoutingRule().apply {
            migration = RoutingRule.MigrationConfig().apply {
                projectLocks = RoutingRule.ProjectLocksConfig(
                    freezeDdl = freezeDdl, freezeDdlInstances = freezeDdlInstances
                )
            }
        }
    }

    private fun guard(resolveTo: String?, rule: RoutingRule?): MigrationDdlGuard {
        val registry: MongoRoutingRegistry = mockk()
        every { registry.resolveRuleName(any()) } returns resolveTo
        val properties = MongoMultiInstanceProperties()
        if (rule != null) properties.rules = mapOf("node" to rule)
        return MigrationDdlGuard(properties, registry)
    }

    @Test
    fun `non-migration collection is allowed`() {
        guard(resolveTo = null, rule = null).assertDdlAllowed("node_0")
    }

    @Test
    fun `freezeDdl false allows any instance`() {
        guard(resolveTo = "node", rule = ruleWith(freezeDdl = false)).assertDdlAllowed("node_0", "default")
    }

    @Test
    fun `freezeDdl true but instance not in freeze list is allowed`() {
        guard(resolveTo = "node", rule = ruleWith(freezeDdl = true, freezeDdlInstances = listOf("heavy1")))
            .assertDdlAllowed("node_0", "default")
    }

    @Test
    fun `freezeDdl true with empty list blocks default instance`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            guard(resolveTo = "node", rule = ruleWith(freezeDdl = true)).assertDdlAllowed("node_0", "default")
        }
        assert(ex.message!!.contains("DDL blocked"))
    }

    @Test
    fun `freezeDdl true with matching instance label blocks`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            guard(resolveTo = "node", rule = ruleWith(freezeDdl = true, freezeDdlInstances = listOf("default")))
                .assertDdlAllowed("node_0", "default")
        }
        assert(ex.message!!.contains("DDL blocked"))
    }
}
