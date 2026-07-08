package com.tencent.bkrepo.common.mongo.reactive.dao

import com.mongodb.ConnectionString
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.api.routing.DualWriteContext
import com.tencent.bkrepo.common.mongo.api.routing.DualWriteExecutor
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import com.tencent.bkrepo.common.mongo.reactive.routing.MongoReactiveRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.DefaultDualWriteExecutor
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.UpdateDefinition
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.reflect.Proxy
import java.util.Date

class AbstractMongoReactiveDaoRoutingTest {

    @Test
    fun `routed write uses Heavy instead of default when route resolves to Heavy`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate()
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = heavyRoutedRegistry(),
        )
        val entity = TestDocument(projectId = "projectA", name = "node")
        val query = Query(Criteria.where("projectId").`is`("projectA"))
        val update = Update().set("name", "updated")

        dao.save(entity)
        dao.insert(entity)
        dao.insert(listOf(entity))
        dao.remove(query)
        dao.updateFirst(query, update)
        dao.updateMulti(query, update)
        dao.upsert(query, update)
        dao.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            TestDocument::class.java,
        )

        assertEquals(
            listOf(
                "save:test_collection",
                "insert:test_collection",
                "insertMany:test_collection",
                "remove:test_collection",
                "updateFirst:test_collection",
                "updateMulti:test_collection",
                "upsert:test_collection",
                "findAndModify:test_collection",
            ),
            heavyTemplate.calls,
        )
        assertTrue(defaultTemplate.calls.isEmpty())
        assertEquals(8, dao.selectorCalls.size)
        assertSame(entity, dao.selectorCalls[0].second)
        assertSame(query, dao.selectorCalls[3].second)
    }

    @Test
    fun `zombie protection throws via dao write path when route primary is default while project is routed out`() {
        val registry = zombieDefaultPrimaryRegistry()
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val route = registry.resolveWriteRoute("artifact_oplog_202501", null, defaultTemplate)
        val dao = ZombieGuardReactiveDao(
            defaultTemplate = defaultTemplate,
            registry = registry,
            targetCollectionName = "artifact_oplog_202501",
            routedProjectId = "projectA",
            routedRuleName = "node",
        )
        val entity = TestDocument(projectId = "projectA", name = "node")

        assertTrue(route.isDefaultInstance)

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking { dao.save(entity) }
        }

        assertTrue(exception.message!!.contains("WRITE_PROTECTION"))
    }

    @Test
    fun `migration in progress path uses default through real registry path`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate()
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = noneDualWriteOffloadPrimaryRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService().first)
        val entity = TestDocument(projectId = "projectA", name = "node")
        val query = Query(Criteria.where("projectId").`is`("projectA"))
        val update = Update().set("name", "updated")

        dao.save(entity)
        dao.insert(entity)
        dao.insert(listOf(entity))
        dao.remove(query)
        dao.updateFirst(query, update)
        dao.updateMulti(query, update)
        dao.upsert(query, update)
        dao.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            TestDocument::class.java,
        )

        assertEquals(
            listOf(
                "save:test_collection",
                "insert:test_collection",
                "insertMany:test_collection",
                "remove:test_collection",
                "updateFirst:test_collection",
                "updateMulti:test_collection",
                "upsert:test_collection",
                "findAndModify:test_collection",
            ),
            defaultTemplate.calls,
        )
        assertEquals(
            listOf(
                "save:test_collection",
                "insert:test_collection",
                "insertMany:test_collection",
                "remove:test_collection",
                "updateFirst:test_collection",
                "updateMulti:test_collection",
                "upsert:test_collection",
                "findAndModify:test_collection",
            ),
            heavyTemplate.calls,
        )
    }

    @Test
    fun `secondary save failure enqueues save compensation targeting offload`() = runBlocking {
        // 模式一双写：主路径 = Default，副路径 = Offload；副路径失败才入补偿
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("save"))
        val (compensationService, compensationTemplate) = compensationService()
        val entity = TestDocument(projectId = "projectA", name = "node")
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = noneDualWriteOffloadPrimaryRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.save(entity)

        assertEquals(listOf("save:test_collection"), defaultTemplate.calls)
        assertEquals(listOf("save:test_collection"), heavyTemplate.calls)
        val task = compensationTemplate.inserted.single()
        assertEquals("SAVE", task.getString("operationType"))
        assertEquals("artifact-oplog", task.getString("ruleName"))
        assertEquals("test_collection", task.getString("collectionName"))
        assertEquals(false, task.getBoolean("targetUseDefault"))
        assertEquals("offload", task.getString("targetInstance"))
    }

    @Test
    fun `secondary insert failure enqueues insert compensation targeting offload`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("insert"))
        val (compensationService, compensationTemplate) = compensationService()
        val entity = TestDocument(projectId = "projectA", name = "node")
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = noneDualWriteOffloadPrimaryRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.insert(entity)

        assertEquals(listOf("insert:test_collection"), defaultTemplate.calls)
        assertEquals(listOf("insert:test_collection"), heavyTemplate.calls)
        val task = compensationTemplate.inserted.single()
        val entityDoc = task.get("entity", Document::class.java)
        assertEquals("INSERT", task.getString("operationType"))
        assertEquals("artifact-oplog", task.getString("ruleName"))
        assertEquals("test_collection", task.getString("collectionName"))
        assertEquals(false, task.getBoolean("targetUseDefault"))
        assertEquals("offload", task.getString("targetInstance"))
        assertEquals("projectA", entityDoc.getString("projectId"))
        assertEquals("node", entityDoc.getString("name"))
    }

    @Test
    fun `compensation insert entity preserves _id assigned by primary insert`() = runBlocking {
        // ponytail: 模拟 MongoTemplate.insert() 原地赋 _id，验证补偿任务 entity 携带 _id，
        // 防止泛型链路 _id 丢失导致 Default/Offload 数据永久分叉
        val simulatedId = "507f1f77bcf86cd799439011"
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(
            failures = setOf("insert"),
            onInsert = { entity -> (entity as? TestDocument)?._id = simulatedId },
        )
        val (compensationService, compensationTemplate) = compensationService()
        val entity = TestDocument(projectId = "projectA", name = "node")
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = projectDualWriteRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.insert(entity)

        assertEquals(listOf("insert:test_collection"), heavyTemplate.calls)
        assertEquals(listOf("insert:test_collection"), defaultTemplate.calls)
        val task = compensationTemplate.inserted.single()
        val entityDoc = task.get("entity", Document::class.java)
        assertEquals(simulatedId, entityDoc.getString("_id"))
    }

    @Test
    fun `secondary remove failure enqueues remove compensation`() = runBlocking {
        // 模式二双写：主路径 = Default，副路径 = Heavy；副路径（Heavy）失败才入补偿
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("remove"))
        val (compensationService, compensationTemplate) = compensationService()
        val query = Query(Criteria.where("projectId").`is`("projectA"))
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = projectDualWriteRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.remove(query)

        assertEquals(listOf("remove:test_collection"), heavyTemplate.calls)
        assertEquals(listOf("remove:test_collection"), defaultTemplate.calls)
        val task = compensationTemplate.inserted.single()
        assertEquals("REMOVE", task.getString("operationType"))
        assertEquals("node", task.getString("ruleName"))
        assertEquals("projectA", task.getString("routingKey"))
        assertEquals(false, task.getBoolean("targetUseDefault"))
        assertEquals("heavy1", task.getString("targetInstance"))
        assertEquals(query.queryObject, task.get("query", Document::class.java))
        assertEquals(TestDocument::class.java.name, task.getString("entityClass"))
    }

    @Test
    fun `secondary updateFirst failure enqueues updateFirst compensation`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("updateFirst"))
        val (compensationService, compensationTemplate) = compensationService()
        val query = Query(Criteria.where("projectId").`is`("projectA"))
        val update = Update().set("name", "updated")
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = projectDualWriteRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.updateFirst(query, update)

        assertEquals(listOf("updateFirst:test_collection"), heavyTemplate.calls)
        assertEquals(listOf("updateFirst:test_collection"), defaultTemplate.calls)
        val task = compensationTemplate.inserted.single()
        assertEquals("UPDATE_FIRST", task.getString("operationType"))
        assertEquals("node", task.getString("ruleName"))
        assertEquals("projectA", task.getString("routingKey"))
        assertEquals(false, task.getBoolean("targetUseDefault"))
        assertEquals("heavy1", task.getString("targetInstance"))
        assertEquals(query.queryObject, task.get("query", Document::class.java))
        assertEquals(update.updateObject, task.get("update", Document::class.java))
    }

    @Test
    fun `secondary updateMulti failure enqueues updateMulti compensation`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("updateMulti"))
        val (compensationService, compensationTemplate) = compensationService()
        val query = Query(Criteria.where("projectId").`is`("projectA"))
        val update = Update().set("name", "updated")
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = projectDualWriteRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.updateMulti(query, update)

        assertEquals(listOf("updateMulti:test_collection"), heavyTemplate.calls)
        assertEquals(listOf("updateMulti:test_collection"), defaultTemplate.calls)
        val task = compensationTemplate.inserted.single()
        assertEquals("UPDATE_MULTI", task.getString("operationType"))
        assertEquals("node", task.getString("ruleName"))
        assertEquals("projectA", task.getString("routingKey"))
        assertEquals(false, task.getBoolean("targetUseDefault"))
        assertEquals("heavy1", task.getString("targetInstance"))
        assertEquals(query.queryObject, task.get("query", Document::class.java))
        assertEquals(update.updateObject, task.get("update", Document::class.java))
    }

    @Test
    fun `secondary upsert failure enqueues upsert compensation`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("upsert"))
        val (compensationService, compensationTemplate) = compensationService()
        val query = Query(Criteria.where("projectId").`is`("projectA"))
        val update = Update().set("name", "updated")
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = projectDualWriteRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.upsert(query, update)

        assertEquals(listOf("upsert:test_collection"), heavyTemplate.calls)
        assertEquals(listOf("upsert:test_collection"), defaultTemplate.calls)
        val task = compensationTemplate.inserted.single()
        assertEquals("UPSERT", task.getString("operationType"))
        assertEquals("node", task.getString("ruleName"))
        assertEquals("projectA", task.getString("routingKey"))
        assertEquals(false, task.getBoolean("targetUseDefault"))
        assertEquals("heavy1", task.getString("targetInstance"))
        assertEquals(query.queryObject, task.get("query", Document::class.java))
        assertEquals(update.updateObject, task.get("update", Document::class.java))
    }

    @Test
    fun `secondary findAndModify failure enqueues compensation with expected arguments`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("findAndModify"))
        val (compensationService, compensationTemplate) = compensationService()
        val query = Query(Criteria.where("projectId").`is`("projectA"))
        val update = Update().set("name", "updated")
        val options = FindAndModifyOptions.options().returnNew(true)
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = projectDualWriteRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.findAndModify(query, update, options, TestDocument::class.java)

        assertEquals(listOf("findAndModify:test_collection"), heavyTemplate.calls)
        assertEquals(listOf("findAndModify:test_collection"), defaultTemplate.calls)
        val task = compensationTemplate.inserted.single()
        val optionsDoc = task.get("options", Document::class.java)
        assertEquals("FIND_AND_MODIFY", task.getString("operationType"))
        assertEquals("node", task.getString("ruleName"))
        assertEquals("projectA", task.getString("routingKey"))
        assertEquals(false, task.getBoolean("targetUseDefault"))
        assertEquals("heavy1", task.getString("targetInstance"))
        assertEquals(query.queryObject, task.get("query", Document::class.java))
        assertEquals(update.updateObject, task.get("update", Document::class.java))
        assertEquals(true, optionsDoc.getBoolean("returnNew"))
        assertEquals(false, optionsDoc.getBoolean("upsert"))
        assertEquals(false, optionsDoc.getBoolean("remove"))
    }

    @Test
    fun `default dual write executor sees real route metadata through reactive bridge`() {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = defaultTemplate,
            registry = projectDualWriteRegistry(defaultTemplate),
        )
        dao.installDualWriteSupport(
            DefaultDualWriteExecutor(zombieAwareRoutingRegistry()),
            compensationService().first,
        )
        val entity = TestDocument(projectId = "projectA", name = "node")

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking { dao.save(entity) }
        }

        assertTrue(exception.message!!.contains("WRITE_PROTECTION"))
        assertTrue(defaultTemplate.calls.isEmpty())
    }

    @Test
    fun `batch insert secondary insertMany failure enqueues compensation per item`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("insertMany"))
        val (compensationService, compensationTemplate) = compensationService()
        val entities = listOf(
            TestDocument(projectId = "projectA", name = "node-a"),
            TestDocument(projectId = "projectA", name = "node-b"),
        )
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = projectDualWriteRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(DefaultDualWriteExecutor(stubRoutingRegistry()), compensationService)

        dao.insert(entities)

        assertEquals(listOf("insertMany:test_collection"), heavyTemplate.calls)
        assertEquals(listOf("insertMany:test_collection"), defaultTemplate.calls)
        assertEquals(2, compensationTemplate.inserted.size)
        val names = compensationTemplate.inserted.map {
            it.get("entity", Document::class.java).getString("name")
        }
        assertEquals(listOf("node-a", "node-b"), names)
        compensationTemplate.inserted.forEach { task ->
            assertEquals("INSERT", task.getString("operationType"))
            assertEquals("node", task.getString("ruleName"))
            assertEquals("projectA", task.getString("routingKey"))
            assertEquals(false, task.getBoolean("targetUseDefault"))
            assertEquals("heavy1", task.getString("targetInstance"))
        }
    }

    @Test
    fun `dual write support still uses single write when route has no secondary`() = runBlocking {
        val defaultTemplate = RecordingReactiveMongoTemplate(failures = setOf("save"))
        val heavyTemplate = RecordingReactiveMongoTemplate()
        val (compensationService, compensationTemplate) = compensationService()
        val entity = TestDocument(projectId = "projectA", name = "node")
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = heavyRoutedRegistry(),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.save(entity)

        assertEquals(listOf("save:test_collection"), heavyTemplate.calls)
        assertTrue(defaultTemplate.calls.isEmpty())
        assertTrue(compensationTemplate.inserted.isEmpty())
    }

    @Test
    fun `compensation entity preserves Date Long nested object types in Document`() = runBlocking {
        // ponytail: 验证补偿链路 entity 经 mongoConverter.write→Document 后
        // Date/Long/嵌套对象等类型正确存储，防止 Jackson 类型转换导致精度/类型丢失
        val now = Date()
        val nested = NestedDoc(key = "k1", score = 99L)
        val defaultTemplate = RecordingReactiveMongoTemplate()
        val heavyTemplate = RecordingReactiveMongoTemplate(failures = setOf("insert"))
        val (compensationService, compensationTemplate) = compensationService()
        val entity = ComplexDoc(
            projectId = "projectA",
            name = "node",
            createdAt = now,
            totalCount = Long.MAX_VALUE,
            nested = nested,
        )
        val dao = RegistryBackedReactiveDao(
            defaultTemplate = defaultTemplate,
            heavyTemplate = heavyTemplate,
            registry = noneDualWriteOffloadPrimaryRegistry(heavyTemplate),
        )
        dao.installDualWriteSupport(ImmediateDualWriteExecutor(), compensationService)

        dao.insert(entity)

        assertEquals(listOf("insert:test_collection"), heavyTemplate.calls)
        assertEquals(listOf("insert:test_collection"), defaultTemplate.calls)
        val task = compensationTemplate.inserted.single()
        val entityDoc = task.get("entity", Document::class.java)
        assertEquals("projectA", entityDoc.getString("projectId"))
        assertEquals("node", entityDoc.getString("name"))
        // Date: 须存储为 Date 类型，非 Long 时间戳或 String
        val storedDate = entityDoc.get("createdAt")
        assertTrue(storedDate is Date, "createdAt should be Date, got ${storedDate?.javaClass?.simpleName}")
        assertEquals(now, storedDate)
        // Long: 须存储为 Long 类型，非 Integer 降级
        val storedCount = entityDoc.get("totalCount")
        assertTrue(storedCount is Long, "totalCount should be Long, got ${storedCount?.javaClass?.simpleName}")
        assertEquals(Long.MAX_VALUE, storedCount)
        // Nested: 嵌套对象字段可访问
        val storedNested = entityDoc.get("nested")
        assertNotNull(storedNested)
        assertTrue(storedNested is NestedDoc, "nested should be NestedDoc, got ${storedNested?.javaClass?.simpleName}")
        assertEquals("k1", (storedNested as NestedDoc).key)
        assertEquals(99L, storedNested.score)
    }

    private class RegistryBackedReactiveDao(
        private val defaultTemplate: RecordingReactiveMongoTemplate,
        private val heavyTemplate: RecordingReactiveMongoTemplate,
        registry: MongoReactiveRoutingRegistry,
        private val targetCollectionName: String = COLLECTION,
    ) : AbstractMongoReactiveDao<TestDocument>() {

        val selectorCalls = mutableListOf<Pair<String, Any?>>()

        init {
            val field = AbstractMongoReactiveDao::class.java.getDeclaredField("reactiveRoutingRegistry")
            field.isAccessible = true
            field.set(this, registry)
        }

        fun installDualWriteSupport(
            executor: DualWriteExecutor,
            compensationService: MongoDualWriteCompensationService,
        ) {
            setFieldIfPresent("dualWriteExecutor", executor)
            setFieldIfPresent("dualWriteCompensationService", compensationService)
        }

        private fun setFieldIfPresent(name: String, value: Any) {
            runCatching { AbstractMongoReactiveDao::class.java.getDeclaredField(name) }
                .getOrNull()
                ?.also {
                    it.isAccessible = true
                    it.set(this, value)
                }
        }

        override fun determineReactiveMongoOperations(): ReactiveMongoTemplate = defaultTemplate

        override fun determineReactiveMongoOperations(
            collectionName: String,
            context: Any?,
        ): ReactiveMongoTemplate {
            selectorCalls += collectionName to context
            val routed = super.determineReactiveMongoOperations(collectionName, context)
            return if (routed === defaultTemplate) defaultTemplate else heavyTemplate
        }

        override fun determineCollectionName(query: Query): String = targetCollectionName

        override fun determineCollectionName(entity: TestDocument): String = targetCollectionName

        override fun determineCollectionName(aggregation: Aggregation): String = targetCollectionName
    }

    private class PlainRegistryReactiveDao(
        private val defaultTemplate: ReactiveMongoTemplate,
        registry: MongoReactiveRoutingRegistry,
        private val targetCollectionName: String = COLLECTION,
    ) : AbstractMongoReactiveDao<TestDocument>() {

        init {
            val field = AbstractMongoReactiveDao::class.java.getDeclaredField("reactiveRoutingRegistry")
            field.isAccessible = true
            field.set(this, registry)
        }

        override fun determineReactiveMongoOperations(): ReactiveMongoTemplate = defaultTemplate

        override fun determineCollectionName(query: Query): String = targetCollectionName

        override fun determineCollectionName(entity: TestDocument): String = targetCollectionName

        override fun determineCollectionName(aggregation: Aggregation): String = targetCollectionName
    }

    private class ZombieGuardReactiveDao(
        private val defaultTemplate: ReactiveMongoTemplate,
        private val registry: MongoReactiveRoutingRegistry,
        private val targetCollectionName: String,
        private val routedProjectId: String,
        private val routedRuleName: String,
    ) : AbstractMongoReactiveDao<TestDocument>() {

        override fun determineReactiveMongoOperations(): ReactiveMongoTemplate = defaultTemplate

        override fun determineReactiveMongoOperations(
            collectionName: String,
            context: Any?,
        ): ReactiveMongoTemplate {
            val route = registry.resolveWriteRoute(collectionName, context, defaultTemplate)
            registry.assertWriteNotZombie(
                route.copy(routingKey = routedProjectId, ruleName = routedRuleName),
                collectionName,
                defaultTemplate,
            )
            return defaultTemplate
        }

        override fun determineCollectionName(query: Query): String = targetCollectionName

        override fun determineCollectionName(entity: TestDocument): String = targetCollectionName

        override fun determineCollectionName(aggregation: Aggregation): String = targetCollectionName
    }

    private open class RecordingReactiveMongoTemplate(
        private val failures: Set<String> = emptySet(),
        private val onInsert: ((Any) -> Unit)? = null,
    ) : ReactiveMongoTemplate(
        SimpleReactiveMongoDatabaseFactory(ConnectionString("mongodb://localhost:27017/test")),
    ) {

        val calls = mutableListOf<String>()

        private fun <T : Any> mono(operation: String, value: T): Mono<T> {
            return if (operation in failures) {
                Mono.error(IllegalStateException("$operation failed"))
            } else {
                Mono.just(value)
            }
        }

        override fun <T : Any> save(objectToSave: T, collectionName: String): Mono<T> {
            calls += "save:$collectionName"
            return mono("save", objectToSave)
        }

        override fun <T : Any> insert(objectToSave: T, collectionName: String): Mono<T> {
            calls += "insert:$collectionName"
            onInsert?.invoke(objectToSave)
            return mono("insert", objectToSave)
        }

        override fun <T : Any> insert(batchToSave: Collection<T>, collectionName: String): Flux<T> {
            calls += "insertMany:$collectionName"
            return if ("insertMany" in failures) {
                Flux.error(IllegalStateException("insertMany failed"))
            } else {
                Flux.fromIterable(batchToSave)
            }
        }

        override fun remove(query: Query, entityClass: Class<*>?, collectionName: String): Mono<DeleteResult> {
            calls += "remove:$collectionName"
            return mono("remove", DeleteResult.acknowledged(1))
        }

        override fun updateFirst(
            query: Query,
            update: UpdateDefinition,
            collectionName: String,
        ): Mono<UpdateResult> {
            calls += "updateFirst:$collectionName"
            return mono("updateFirst", UpdateResult.acknowledged(1, 1L, null))
        }

        override fun updateMulti(
            query: Query,
            update: UpdateDefinition,
            collectionName: String,
        ): Mono<UpdateResult> {
            calls += "updateMulti:$collectionName"
            return mono("updateMulti", UpdateResult.acknowledged(1, 1L, null))
        }

        override fun upsert(
            query: Query,
            update: UpdateDefinition,
            collectionName: String,
        ): Mono<UpdateResult> {
            calls += "upsert:$collectionName"
            return mono("upsert", UpdateResult.acknowledged(1, 1L, null))
        }

        override fun <T : Any> findAndModify(
            query: Query,
            update: UpdateDefinition,
            options: FindAndModifyOptions,
            entityClass: Class<T>,
            collectionName: String,
        ): Mono<T> {
            calls += "findAndModify:$collectionName"
            @Suppress("UNCHECKED_CAST")
            return mono("findAndModify", TestDocument() as T)
        }
    }

    private class ImmediateDualWriteExecutor : DualWriteExecutor {
        override fun <T> execute(
            context: DualWriteContext,
            primary: () -> T,
            secondary: (primaryResult: T) -> Unit,
        ): T {
            val result = primary()
            runCatching { secondary(result) }
                .onFailure { context.enqueueOnFailure() }
            return result
        }
    }

    private data class TestDocument(
        val projectId: String = "",
        val name: String = "",
        var _id: String? = null,
    )

    private data class NestedDoc(
        val key: String = "",
        val score: Long = 0L,
    )

    private data class ComplexDoc(
        val projectId: String = "",
        val name: String = "",
        val createdAt: Date = Date(),
        val totalCount: Long = 0L,
        val nested: NestedDoc = NestedDoc(),
    )

    private class RecordingMongoTemplate : MongoTemplate(
        SimpleMongoClientDatabaseFactory("mongodb://localhost:27017/test"),
    ) {
        val inserted = mutableListOf<Document>()

        override fun <T : Any> insert(objectToSave: T, collectionName: String): T {
            if (objectToSave is Document) {
                inserted += Document(objectToSave)
            }
            return objectToSave
        }

        override fun count(query: Query, collectionName: String): Long = inserted.size.toLong()
    }

    private companion object {
        private const val COLLECTION = "test_collection"

        private fun compensationService(): Pair<MongoDualWriteCompensationService, RecordingMongoTemplate> {
            val template = RecordingMongoTemplate()
            return MongoDualWriteCompensationService(
                template,
                mongoConverter(),
                stubRoutingRegistry(),
                MongoMultiInstanceProperties(),
            ) to template
        }

        private fun mongoConverter(): MongoConverter {
            return Proxy.newProxyInstance(
                MongoConverter::class.java.classLoader,
                arrayOf(MongoConverter::class.java),
            ) { _, method, args ->
                if (method.name == "write") {
                    val source = args[0]
                    val target = args[1] as Document
                    source.javaClass.declaredFields.forEach { field ->
                        field.isAccessible = true
                        target[field.name] = field.get(source)
                    }
                    return@newProxyInstance null
                }
                when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    List::class.java -> emptyList<Any>()
                    Set::class.java -> emptySet<Any>()
                    Map::class.java -> emptyMap<Any, Any>()
                    else -> null
                }
            } as MongoConverter
        }

        private fun stubRoutingRegistry(): MongoRoutingRegistry {
            return Proxy.newProxyInstance(
                MongoRoutingRegistry::class.java.classLoader,
                arrayOf(MongoRoutingRegistry::class.java),
            ) { _, method, _ ->
                when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    String::class.java -> ""
                    List::class.java -> emptyList<Any>()
                    Set::class.java -> emptySet<Any>()
                    Map::class.java -> emptyMap<Any, Any>()
                    else -> null
                }
            } as MongoRoutingRegistry
        }

        private fun zombieAwareRoutingRegistry(): MongoRoutingRegistry {
            return Proxy.newProxyInstance(
                MongoRoutingRegistry::class.java.classLoader,
                arrayOf(MongoRoutingRegistry::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "resolveRuleName" -> "node"
                    "isProjectRoutedOut" -> args[1] == "projectA"
                    else -> when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        String::class.java -> ""
                        List::class.java -> emptyList<Any>()
                        Set::class.java -> emptySet<Any>()
                        Map::class.java -> emptyMap<Any, Any>()
                        else -> null
                    }
                }
            } as MongoRoutingRegistry
        }

        private fun noneDualWriteOffloadPrimaryRegistry(
            heavyTemplate: ReactiveMongoTemplate,
        ): MongoReactiveRoutingRegistry = registryWithTemplates(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "artifact-oplog" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.NONE,
                        collectionPrefix = "test_",
                        routingState = RuleRoutingState.DUAL_WRITE,
                        instances = mapOf(
                            "offload" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = "mongodb://offload-primary:27017/test",
                            )
                        ),
                    )
                )
            },
            mapOf("artifact-oplog" to mapOf("offload" to heavyTemplate)),
        )

        private fun heavyRoutedRegistry(): MongoReactiveRoutingRegistry = registryWithTemplates(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "test_",
                        routingKeyField = "projectId",
                        routingState = RuleRoutingState.ROUTED,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = "mongodb://heavy-primary:27017/test",
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
            emptyMap(),
        )

        private fun projectDualWriteRegistry(
            heavyTemplate: ReactiveMongoTemplate,
        ): MongoReactiveRoutingRegistry = registryWithTemplates(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "test_",
                        routingKeyField = "projectId",
                        routingState = RuleRoutingState.DUAL_WRITE,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = "mongodb://heavy-primary:27017/test",
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
            mapOf("node" to mapOf("heavy1" to heavyTemplate)),
        )

        private fun zombieDefaultPrimaryRegistry(): MongoReactiveRoutingRegistry = MongoReactiveRoutingRegistry(
            MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "artifact-oplog" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.NONE,
                        collectionPrefix = "artifact_oplog_",
                        routingState = RuleRoutingState.DUAL_WRITE,
                        instances = mapOf(
                            "offload" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = "mongodb://offload-primary:27017/test",
                            )
                        ),
                    ),
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingKeyField = "projectId",
                        routingState = RuleRoutingState.ROUTED,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = "mongodb://heavy-primary:27017/test",
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            },
        )

        private fun registryWithTemplates(
            properties: MongoMultiInstanceProperties,
            primaryTemplates: Map<String, Map<String, ReactiveMongoTemplate>>,
        ): MongoReactiveRoutingRegistry {
            val registry = MongoReactiveRoutingRegistry(properties)
            if (primaryTemplates.isNotEmpty()) {
                val field = MongoReactiveRoutingRegistry::class.java.getDeclaredField("primaryTemplates")
                field.isAccessible = true
                field.set(registry, primaryTemplates)
            }
            return registry
        }
    }
}