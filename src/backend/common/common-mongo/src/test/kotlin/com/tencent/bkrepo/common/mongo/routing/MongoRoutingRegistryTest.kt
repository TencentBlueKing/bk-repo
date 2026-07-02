package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * MongoRoutingRegistry 路由决策矩阵单元测试。
 *
 * 覆盖 Spec §3.6.2 路由决策矩阵所有分支，以及 projectId 提取逻辑（§16.1）：
 *  1. routing-enabled=false → null（回退 Default）
 *  2. PROJECT + projectId 命中 project-routing → Heavy primary
 *  3. PROJECT + 未命中 project-routing + 命中 shard-routing → Heavy primary
 *  4. PROJECT + 均未命中 → null（回退 Default）
 *  5. NONE + dualWrite=false → oplog 专属实例 primary
 *  6. NONE + dualWrite=true（双写期） → null（读/写仍走 Default）
 *  7. resolveWriteRoute, PROJECT + dualWrite=true → WriteRoute(Heavy, Default)
 *  8. resolveWriteRoute, NONE + dualWrite=true → WriteRoute(Default, Heavy)
 *  9. projectId 从 Query.queryObject 顶层提取
 * 10. projectId 从实体反射提取
 * 11. validateOnStartup：引用不存在的 instance 时 fail-fast
 */
class MongoRoutingRegistryTest {

    // 使用假 URI：SimpleMongoClientDatabaseFactory 连接懒建立，路由逻辑测试无需真实 MongoDB
    private val heavyUri = "mongodb://heavy-primary:27017/test"
    private val heavySecUri = "mongodb://heavy-secondary:27017/test"
    private val oplogUri = "mongodb://oplog-primary:27017/test"
    private val oplogSecUri = "mongodb://oplog-secondary:27017/test"

    private lateinit var nodeRegistry: MongoRoutingRegistry
    private lateinit var oplogRegistryNoDualWrite: MongoRoutingRegistry
    private lateinit var oplogRegistryDualWrite: MongoRoutingRegistry
    private lateinit var disabledRegistry: MongoRoutingRegistry

    @BeforeEach
    fun setUp() {
        nodeRegistry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingKeyField = "projectId",
                        routingEnabled = true,
                        dualWrite = false,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = heavyUri,
                                secondaryUri = heavySecUri,
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                        shardRouting = mapOf("node_188" to "heavy1"),
                    )
                )
            }
        )
        oplogRegistryNoDualWrite = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "artifact-oplog" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.NONE,
                        collectionPrefix = "artifact_oplog_",
                        routingEnabled = true,
                        dualWrite = false,
                        instances = mapOf(
                            "oplog" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = oplogUri,
                                secondaryUri = oplogSecUri,
                            )
                        ),
                    )
                )
            }
        )
        oplogRegistryDualWrite = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "artifact-oplog" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.NONE,
                        collectionPrefix = "artifact_oplog_",
                        routingEnabled = true,
                        dualWrite = true,
                        instances = mapOf(
                            "oplog" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = oplogUri,
                                secondaryUri = oplogSecUri,
                            )
                        ),
                    )
                )
            }
        )
        disabledRegistry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = false,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri)
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            }
        )
    }

    // ── 分支 1：routing-enabled=false ────────────────────────────────────────

    @Test
    fun `routeWrite returns null when routing disabled`() {
        assertNull(disabledRegistry.routeWrite("node_0", "projectA"))
    }

    @Test
    fun `routeRead returns null when routing disabled`() {
        assertNull(disabledRegistry.routeRead("node_0", "projectA"))
    }

    // ── 分支 2：PROJECT + 命中 project-routing ───────────────────────────────

    @Test
    fun `routeWrite returns heavy primary when projectId hits project-routing`() {
        val result = nodeRegistry.routeWrite("node_0", "projectA")
        val expected = nodeRegistry.allPrimaryTemplates("node")["heavy1"]
        assertNotNull(result)
        assertSame(expected, result)
    }

    @Test
    fun `routeRead returns heavy secondary when projectId hits project-routing`() {
        val result = nodeRegistry.routeRead("node_0", "projectA")
        val expected = nodeRegistry.allSecondaryTemplates("node")["heavy1"]
        assertNotNull(result)
        assertSame(expected, result)
    }

    // ── 分支 3：PROJECT + 未命中 project-routing + 命中 shard-routing ─────────

    @Test
    fun `routeWrite routes by shard when projectId not in project-routing`() {
        // projectB 没有 project-routing，但 node_188 有 shard-routing → heavy1
        val result = nodeRegistry.routeWrite("node_188", "projectB")
        val expected = nodeRegistry.allPrimaryTemplates("node")["heavy1"]
        assertNotNull(result)
        assertSame(expected, result)
    }

    // ── 分支 4：PROJECT + 均未命中 → null ────────────────────────────────────

    @Test
    fun `routeWrite returns null when neither project-routing nor shard-routing matches`() {
        // projectB 无 project-routing，node_0 无 shard-routing
        assertNull(nodeRegistry.routeWrite("node_0", "projectB"))
    }

    // ── 分支 5：NONE + dualWrite=false → oplog 实例 ─────────────────────────

    @Test
    fun `routeWrite returns oplog primary for NONE routing without dual-write`() {
        val result = oplogRegistryNoDualWrite.routeWrite("artifact_oplog_202501", null)
        val expected = oplogRegistryNoDualWrite.allPrimaryTemplates("artifact-oplog")["oplog"]
        assertNotNull(result)
        assertSame(expected, result)
    }

    @Test
    fun `routeRead returns oplog secondary for NONE routing without dual-write`() {
        val result = oplogRegistryNoDualWrite.routeRead("artifact_oplog_202501", null)
        val expected = oplogRegistryNoDualWrite.allSecondaryTemplates("artifact-oplog")["oplog"]
        assertNotNull(result)
        assertSame(expected, result)
    }

    // ── 分支 6：NONE + dualWrite=true → null（双写期 Default 是主路径）────────

    @Test
    fun `routeWrite returns null during NONE dual-write period`() {
        assertNull(oplogRegistryDualWrite.routeWrite("artifact_oplog_202501", null))
    }

    @Test
    fun `routeRead returns null during NONE dual-write period`() {
        assertNull(oplogRegistryDualWrite.routeRead("artifact_oplog_202501", null))
    }

    // ── 分支 7：resolveWriteRoute, PROJECT + dualWrite=true ──────────────────

    @Test
    fun `resolveWriteRoute returns heavy-primary as primary and default as secondary for PROJECT dual-write`() {
        val dualWriteNodeRegistry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = heavyUri,
                                secondaryUri = heavySecUri,
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            }
        )
        val fakeDefault = dualWriteNodeRegistry.allPrimaryTemplates("node")["heavy1"]!! // reuse as default stand-in
        val route = dualWriteNodeRegistry.resolveWriteRoute("node_0", "projectA", fakeDefault)
        // primary = Heavy（先写），secondary ≠ null（副路径写 Default）
        assertNotNull(route.secondary)
    }

    // ── 分支 8：resolveWriteRoute, NONE + dualWrite=true ─────────────────────

    @Test
    fun `resolveWriteRoute returns default as primary and oplog as secondary for NONE dual-write`() {
        val fakeDefault = oplogRegistryDualWrite.allPrimaryTemplates("artifact-oplog")["oplog"]!!
        val route = oplogRegistryDualWrite.resolveWriteRoute("artifact_oplog_202501", null, fakeDefault)
        // NONE 双写期：主路径=Default，副路径=oplog 实例
        assertSame(fakeDefault, route.primary)
        val oplogPrimary = oplogRegistryDualWrite.allPrimaryTemplates("artifact-oplog")["oplog"]
        assertSame(oplogPrimary, route.secondary)
    }

    // ── projectId 提取：Query.queryObject 顶层 ───────────────────────────────

    @Test
    fun `routeWrite extracts projectId from Query queryObject top-level`() {
        val query = Query(Criteria.where("projectId").`is`("projectA"))
        val result = nodeRegistry.routeWrite("node_5", query)
        val expected = nodeRegistry.allPrimaryTemplates("node")["heavy1"]
        assertSame(expected, result)
    }

    @Test
    fun `routeWrite returns null when Query has no projectId field`() {
        val query = Query(Criteria.where("name").`is`("foo"))
        assertNull(nodeRegistry.routeWrite("node_5", query))
    }

    @Test
    fun `routeWrite extracts projectId from Query with dollar and clause`() {
        val query = Query(
            Criteria().andOperator(
                Criteria.where("status").`is`("active"),
                Criteria.where("projectId").`is`("projectA"),
            )
        )
        val result = nodeRegistry.routeWrite("node_5", query)
        val expected = nodeRegistry.allPrimaryTemplates("node")["heavy1"]
        assertSame(expected, result)
    }

    @Test
    fun `routeWrite returns null when Query uses dollar or with projectId`() {
        val query = Query(
            Criteria().orOperator(
                Criteria.where("projectId").`is`("projectA"),
                Criteria.where("projectId").`is`("projectB"),
            )
        )
        assertNull(nodeRegistry.routeWrite("node_5", query))
    }

    @Test
    fun `routeWrite returns null for empty Query`() {
        assertNull(nodeRegistry.routeWrite("node_5", Query()))
    }

    // ── projectId 提取：实体反射 ─────────────────────────────────────────────

    @Test
    fun `routeWrite extracts routingKey from entity via reflection`() {
        data class FakeNode(val projectId: String, val name: String)
        val entity = FakeNode(projectId = "projectA", name = "file.txt")
        val result = nodeRegistry.routeWrite("node_5", entity)
        val expected = nodeRegistry.allPrimaryTemplates("node")["heavy1"]
        assertSame(expected, result)
    }

    @Test
    fun `routeWrite returns null when entity has no matching routing key field`() {
        data class NoProject(val name: String)
        assertNull(nodeRegistry.routeWrite("node_5", NoProject("x")))
    }

    // ── 集合前缀不匹配时 → null ───────────────────────────────────────────────

    @Test
    fun `routeWrite returns null for collection that does not match any prefix`() {
        assertNull(nodeRegistry.routeWrite("package_0", "projectA"))
    }

    // ── validateOnStartup ────────────────────────────────────────────────────

    @Test
    fun `validateOnStartup throws when project-routing references nonexistent instance`() {
        val badRegistry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri)
                        ),
                        projectRouting = mapOf("projectA" to "nonexistent"),
                    )
                )
            }
        )
        org.junit.jupiter.api.assertThrows<IllegalStateException> { badRegistry.validateOnStartup() }
    }

    @Test
    fun `validateOnStartup passes with valid configuration`() {
        nodeRegistry.validateOnStartup()
    }

    @Test
    fun `validateOnStartup throws when project-routing conflicts with shard-routing on same collection`() {
        val shardIdx = HashShardingUtils.shardingSequenceFor("projectA", 256)
        val conflictingCollection = "node_$shardIdx"
        val badRegistry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri)
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                        shardRouting = mapOf(conflictingCollection to "heavy1"),
                    )
                )
            }
        )
        org.junit.jupiter.api.assertThrows<IllegalStateException> { badRegistry.validateOnStartup() }
    }

    @Test
    fun `resolveWriteRoute does not dual-write when dualWrite is false`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = false,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = heavyUri,
                                secondaryUri = heavySecUri,
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
        )
        val fakeDefault = registry.allPrimaryTemplates("node")["heavy1"]!!
        val route = registry.resolveWriteRoute("node_0", "projectA", fakeDefault)
        assertEquals(null, route.secondary)
        assertEquals(false, route.isDefaultInstance)
    }

    @Test
    fun `resolveWriteRoute dual-writes when dualWrite is true`() {
        val dualRegistry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = heavyUri,
                                secondaryUri = heavySecUri,
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
        )
        val fakeDefault = dualRegistry.allPrimaryTemplates("node")["heavy1"]!!
        val route = dualRegistry.resolveWriteRoute("node_0", "projectA", fakeDefault)
        assertNotNull(route.secondary)
        assertEquals(true, route.syncSecondaryWrite)
    }

    // ── routedProjectIds / isDualWrite ───────────────────────────────────────

    @Test
    fun `routedProjectIds returns configured projects when routing enabled`() {
        assertEquals(setOf("projectA"), nodeRegistry.routedProjectIds("node"))
    }

    @Test
    fun `routedProjectIds returns empty when routing disabled`() {
        assertEquals(emptySet<String>(), disabledRegistry.routedProjectIds("node"))
    }

    @Test
    fun `isDualWrite returns false when dualWrite not set`() {
        assertEquals(false, nodeRegistry.isDualWrite("node"))
    }

    // ── resolveReadRoute: DUAL_WRITE 项目读 Default ────────────────────────

    @Test
    fun `resolveReadRoute returns Default for DUAL_WRITE project`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = heavyUri,
                                secondaryUri = heavySecUri,
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
        )
        val fakeDefault = registry.allPrimaryTemplates("node")["heavy1"]!!
        val route = registry.resolveReadRoute("node_0", "projectA", fakeDefault)
        assertSame(fakeDefault, route.template)
    }

    @Test
    fun `resolveReadRoute returns Heavy for ROUTED project`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = false,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = heavyUri,
                                secondaryUri = heavySecUri,
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
        )
        val fakeDefault = registry.allPrimaryTemplates("node")["heavy1"]!!
        val route = registry.resolveReadRoute("node_0", "projectA", fakeDefault)
        // ROUTED 后读 Heavy Secondary
        assertNotSame(fakeDefault, route.template)
    }

    // ── resolveRuleName ────────────────────────────────────────────────────

    @Test
    fun `resolveRuleName returns rule name for matching prefix`() {
        assertEquals("node", nodeRegistry.resolveRuleName("node_0"))
        assertEquals("node", nodeRegistry.resolveRuleName("node_255"))
    }

    @Test
    fun `resolveRuleName returns null for non-matching prefix`() {
        assertNull(nodeRegistry.resolveRuleName("package_0"))
        assertNull(nodeRegistry.resolveRuleName("repository"))
    }

    // ── isNoneRoutingMode ──────────────────────────────────────────────────

    @Test
    fun `isNoneRoutingMode is true for NONE type`() {
        assertTrue(oplogRegistryNoDualWrite.isNoneRoutingMode("artifact-oplog"))
    }

    @Test
    fun `isNoneRoutingMode is false for PROJECT type`() {
        assertFalse(nodeRegistry.isNoneRoutingMode("node"))
    }

    // ── allKnownProjectIds ─────────────────────────────────────────────────

    @Test
    fun `allKnownProjectIds returns all configured projects regardless of routing state`() {
        val projectIds = nodeRegistry.allKnownProjectIds("node")
        assertTrue(projectIds.contains("projectA"))
    }

    @Test
    fun `allKnownProjectIds returns empty for unknown rules`() {
        assertEquals(emptySet<String>(), nodeRegistry.allKnownProjectIds("unknown-rule"))
    }

    // ── hasRoutedProjects ───────────────────────────────────────────────────

    @Test
    fun `hasRoutedProjects returns true when routing is enabled with projects`() {
        assertTrue(nodeRegistry.hasRoutedProjects("node"))
    }

    @Test
    fun `hasRoutedProjects returns false when routing is disabled`() {
        assertFalse(disabledRegistry.hasRoutedProjects("node"))
    }

    // ── isProjectRoutedOut ──────────────────────────────────────────────────

    @Test
    fun `isProjectRoutedOut returns true when dualWrite is false`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = false,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri)
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
        )
        assertTrue(registry.isProjectRoutedOut("node", "projectA"))
    }

    @Test
    fun `isProjectRoutedOut returns false when dualWrite is true`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri)
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
        )
        assertFalse(registry.isProjectRoutedOut("node", "projectA"))
    }

    // ── projectsByInstance ──────────────────────────────────────────────────

    @Test
    fun `projectsByInstance groups projects by instance name`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri),
                            "heavy2" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri),
                        ),
                        projectRouting = mapOf(
                            "projectA" to "heavy1",
                            "projectB" to "heavy1",
                            "projectC" to "heavy2",
                        ),
                    )
                )
            }
        )
        val result = registry.projectsByInstance("node")
        assertEquals(2, result.size)
        assertEquals(setOf("projectA", "projectB"), result["heavy1"])
        assertEquals(setOf("projectC"), result["heavy2"])
    }

    // ── shardRoutedCollections / shardsByInstance ───────────────────────────

    @Test
    fun `shardRoutedCollections returns configured shard collections`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri)
                        ),
                        shardRouting = mapOf("node_188" to "heavy1", "node_200" to "heavy1"),
                    )
                )
            }
        )
        val shards = registry.shardRoutedCollections("node")
        assertTrue(shards.contains("node_188"))
        assertTrue(shards.contains("node_200"))
        assertEquals(2, shards.size)
    }

    @Test
    fun `shardsByInstance groups shards by instance`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri),
                            "heavy2" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri),
                        ),
                        shardRouting = mapOf(
                            "node_0" to "heavy1",
                            "node_1" to "heavy2",
                        ),
                    )
                )
            }
        )
        val result = registry.shardsByInstance("node")
        assertEquals(2, result.size)
        assertEquals(setOf("node_0"), result["heavy1"])
        assertEquals(setOf("node_1"), result["heavy2"])
    }

    // ── config version ───────────────────────────────────────────────────────

    @Test
    fun `configVersion and minConfigVersion are accessible`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                configVersion = 10
                minConfigVersion = 8
                rules = emptyMap()
            }
        )
        assertEquals(10L, registry.getConfigVersion())
        assertEquals(8L, registry.getMinConfigVersion())
        assertTrue(registry.isConfigUpToDate())
    }

    @Test
    fun `isConfigUpToDate returns false when version behind min`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                configVersion = 5
                minConfigVersion = 10
                rules = emptyMap()
            }
        )
        assertFalse(registry.isConfigUpToDate())
    }

    // ── shard-routing + dualWrite 写路由（§3.6.2）────────────────────────────

    @Test
    fun `resolveWriteRoute dual-writes shard-routed collection when dualWrite enabled`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = heavyUri,
                                secondaryUri = heavySecUri,
                            )
                        ),
                        shardRouting = mapOf("node_188" to "heavy1"),
                    )
                )
            }
        )
        val fakeDefault = registry.allPrimaryTemplates("node")["heavy1"]!!
        val route = registry.resolveWriteRoute("node_188", "projectB", fakeDefault)
        assertNotNull(route.secondary)
        assertEquals("projectB", route.routingKey)
    }

    @Test
    fun `resolveWriteRoute throws when PROJECT routing requires projectId`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(uri = heavyUri)
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            }
        )
        val fakeDefault = registry.allPrimaryTemplates("node")["heavy1"]!!
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            registry.resolveWriteRoute("node_0", Query(Criteria.where("name").`is`("x")), fakeDefault)
        }
    }

    @Test
    fun `resolveReadRoute sets fallbackToDefault when fallbackBeforeCleanup enabled`() {
        val registry = DefaultMongoRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingEnabled = true,
                        dualWrite = false,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = heavyUri,
                                secondaryUri = heavySecUri,
                                fallbackBeforeCleanup = true,
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            }
        )
        val fakeDefault = registry.allPrimaryTemplates("node")["heavy1"]!!
        val route = registry.resolveReadRoute("node_0", "projectA", fakeDefault)
        assertTrue(route.fallbackToDefault)
        assertSame(fakeDefault, route.fallbackTemplate)
    }
}