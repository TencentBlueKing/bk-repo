/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.mongo.dao

import com.tencent.bkrepo.common.mongo.api.routing.DualWriteContext
import com.tencent.bkrepo.common.mongo.api.routing.DualWriteExecutor
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteSupport
import com.tencent.bkrepo.common.mongo.routing.MongoRoutingMetrics
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.ReadRoute
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.MongoCollectionUtils.getPreferredCollectionName
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.lang.reflect.ParameterizedType
import java.util.stream.Stream

/**
 * mongo db 数据访问层抽象类
 */
abstract class AbstractMongoDao<E> : MongoDao<E> {

    @Suppress("LateinitUsage")
    @Autowired(required = false)
    private var routingRegistry: MongoRoutingRegistry? = null

    @Suppress("LateinitUsage")
    @Autowired(required = false)
    private var dualWriteCompensationService: MongoDualWriteCompensationService? = null

    @Autowired(required = false)
    private var dualWriteExecutor: DualWriteExecutor? = null

    @Autowired(required = false)
    private var routingMetrics: MongoRoutingMetrics? = null

    /**
     * 实体类Class
     */
    @Suppress("UNCHECKED_CAST")
    protected open val classType = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<E>

    /**
     * 集合名称
     */
    protected open val collectionName: String by lazy { determineCollectionName() }

    fun findOne(query: Query): E? {
        return findOne(query, classType)
    }

    fun find(query: Query): List<E> {
        return find(query, classType)
    }

    fun findAll(): List<E> {
        return findAll(classType)
    }

    fun stream(query: Query): Stream<E> {
        return stream(query, classType)
    }

    override fun <T> findOne(query: Query, clazz: Class<T>): T? {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao findOne: [$query] [$clazz]")
        }
        val col = determineCollectionName(query)
        return executeRead(col, query) { it.findOne(query, clazz, col) }
    }

    override fun <T> find(query: Query, clazz: Class<T>): List<T> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao find: [$query]")
        }
        val col = determineCollectionName(query)
        return executeRead(col, query) { it.find(query, clazz, col) }
    }

    override fun <T> findAll(clazz: Class<T>): List<T> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao find all")
        }
        return executeRead(collectionName, null) { it.findAll(clazz, collectionName) }
    }

    override fun insert(entity: E): E {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao insert: [$entity]")
        }
        val col = determineCollectionName(entity)
        val route = writeRoute(col, entity)
        val result = runDualWrite(route, col,
            enqueue = { dualWriteCompensationService?.enqueueInsert(route, col, entity as Any) },
        ) { it.insert(entity, col) }
        return result
    }

    override fun insert(entityCollection: Collection<E>): Collection<E> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao insert many: [$entityCollection]")
        }
        if (entityCollection.isEmpty()) return entityCollection
        // 批量 insert 可能跨越多个路由键，按 routingKey 分组后分别路由（保持原子性最大化）
        val grouped = entityCollection.groupBy { entity ->
            val col = determineCollectionName(entity)
            val route = writeRoute(col, entity)
            Triple(col, route.primary, route)
        }
        grouped.forEach { (key, batch) ->
            val (col, _, route) = key
            executePrimaryWrite(route, col) { it.insert(batch, col) }
            // 双写副路径：批量同步入队，避免在 executor 内丢失部分元素
            submitSecondaryBatchWrite(route, col, batch) { tmpl, items ->
                tmpl.insert(items, col)
            }
        }
        return entityCollection
    }

    override fun save(entity: E): E {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao save: [$entity]")
        }
        val col = determineCollectionName(entity)
        val route = writeRoute(col, entity)
        val result = runDualWrite(route, col,
            enqueue = { dualWriteCompensationService?.enqueueSave(route, col, entity as Any) },
        ) { it.save(entity, col) }
        return result
    }

    override fun remove(query: Query): DeleteResult {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao remove: [$query]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        val result = runDualWrite(route, col,
            enqueue = { dualWriteCompensationService?.enqueueRemove(route, col, classType.name, query) },
        ) { it.remove(query, classType, col) }
        assertNoneModeMatched(col, route, query, matched = result.deletedCount > 0, op = "remove")
        return result
    }

    override fun updateFirst(query: Query, update: Update): UpdateResult {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao updateFirst: [$query], [$update]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        val result = runDualWrite(route, col,
            enqueue = { dualWriteCompensationService?.enqueueUpdateFirst(route, col, query, update) },
        ) { it.updateFirst(query, update, col) }
        assertNoneModeMatched(col, route, query, matched = result.matchedCount > 0, op = "updateFirst")
        return result
    }

    override fun updateMulti(query: Query, update: Update): UpdateResult {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao updateMulti: [$query], [$update]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        val result = runDualWrite(route, col,
            enqueue = { dualWriteCompensationService?.enqueueUpdateMulti(route, col, query, update) },
        ) { it.updateMulti(query, update, col) }
        assertNoneModeMatched(col, route, query, matched = result.matchedCount > 0, op = "updateMulti")
        return result
    }

    override fun upsert(query: Query, update: Update): UpdateResult {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao upsert: [$query], [$update]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        val result = try {
            runDualWrite(route, col,
                enqueue = { dualWriteCompensationService?.enqueueUpsert(route, col, query, update) },
            ) { it.upsert(query, update, col) }
        } catch (exception: DuplicateKeyException) {
            logger.warn("Upsert error[DuplicateKeyException]: " + exception.message.orEmpty())
            runDualWrite(route, col,
                enqueue = { dualWriteCompensationService?.enqueueUpsert(route, col, query, update) },
            ) { it.upsert(query, update, col) }
        }
        return result
    }

    override fun count(query: Query): Long {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao count: [$query]")
        }
        val col = determineCollectionName(query)
        return executeRead(col, query) { it.count(query, col) }
    }

    override fun exists(query: Query): Boolean {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao exists: [$query]")
        }
        val col = determineCollectionName(query)
        return executeRead(col, query) { it.exists(query, col) }
    }

    override fun <O> aggregate(aggregation: Aggregation, outputType: Class<O>): AggregationResults<O> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao aggregate: [$aggregation], outputType: [$outputType]")
        }
        val col = determineCollectionName(aggregation)
        // aggregate 是只读操作，走 Secondary（与 find/count 一致），未命中路由再回退到 Default Primary
        return executeRead(col, aggregation) { it.aggregate(aggregation, col, outputType) }
    }

    override fun <T> findAndModify(
        query: Query,
        update: Update,
        options: FindAndModifyOptions,
        clazz: Class<T>
    ): T? {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao findAndModify: [$query], [$update]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        val result = runDualWrite(route, col, enqueue = {
            dualWriteCompensationService?.enqueueFindAndModify(
                route, col, query, update, options, clazz.name,
            )
        }) {
            it.findAndModify(query, update, options, clazz, col)
        }
        return result
    }

    override fun <T> stream(query: Query, clazz: Class<T>): Stream<T> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao stream query: [$query]")
        }
        val col = determineCollectionName(query)
        return executeRead(col, query) { it.stream(query, clazz, col) }
    }

    protected open fun determineCollectionName(): String {
        var collectionName: String? = null
        if (classType.isAnnotationPresent(Document::class.java)) {
            val document = classType.getAnnotation(Document::class.java)
            collectionName = if (document.collection.isNotBlank()) document.collection else document.value
        }

        return if (collectionName.isNullOrEmpty()) getPreferredCollectionName(classType) else collectionName
    }

    abstract fun determineMongoTemplate(): MongoTemplate

    /**
     * 解析写操作路由（支持双写）。
     * 返回 [WriteRoute]：primary 是主路径模板，secondary 是双写副路径（null 表示单写）。
     */
    private fun writeRoute(collectionName: String, context: Any?): WriteRoute {
        val default = determineMongoTemplate()
        return routingRegistry?.resolveWriteRoute(collectionName, context, default)
            ?: WriteRoute(default)
    }

    private fun readRoute(collectionName: String, context: Any?): ReadRoute {
        val default = determineMongoTemplate()
        return routingRegistry?.resolveReadRoute(collectionName, context, default)
            ?: ReadRoute(default)
    }

    private fun <T> executeRead(
        collectionName: String,
        context: Any?,
        action: (MongoTemplate) -> T,
    ): T {
        val route = readRoute(collectionName, context)
        return try {
            action(route.template)
        } catch (exception: Exception) {
            val fallback = route.fallbackTemplate
            if (route.fallbackToDefault && fallback != null && fallback !== route.template) {
                logger.warn("Mongo read fallback to Default for [{}]: {}", collectionName, exception.message)
                action(fallback)
            } else {
                throw exception
            }
        }
    }

    private fun <T> runDualWrite(
        route: WriteRoute,
        collectionName: String,
        enqueue: () -> Unit,
        write: (MongoTemplate) -> T,
    ): T {
        val executor = dualWriteExecutor
        if (executor != null && route.secondary != null) {
            return executor.execute(
                DualWriteContext(
                    route = route,
                    collectionName = collectionName,
                    defaultTemplate = determineMongoTemplate(),
                    enqueueOnFailure = enqueue,
                ),
                primary = { write(route.primary) },
                secondary = { write(route.secondary!!); Unit },
            )
        }
        val result = executePrimaryWrite(route, collectionName, write)
        submitSecondaryWrite(route, collectionName, enqueue) { write(it); Unit }
        return result
    }

    private fun <T> executePrimaryWrite(
        route: WriteRoute,
        collectionName: String,
        action: (MongoTemplate) -> T,
    ): T = MongoDualWriteSupport.executePrimaryWrite(
        route, collectionName, determineMongoTemplate(), routingRegistry, action,
    )

    private fun submitSecondaryWrite(
        route: WriteRoute,
        collectionName: String,
        enqueue: () -> Unit,
        action: (MongoTemplate) -> Unit,
    ) = MongoDualWriteSupport.submitSecondaryWrite(route, collectionName, enqueue, action)

    /**
     * 批量 insert 的双写副路径。
     * 失败时按元素入队补偿（保持与单条 insert 路径一致的语义）。
     */
    private fun submitSecondaryBatchWrite(
        route: WriteRoute,
        collectionName: String,
        items: List<E>,
        action: (MongoTemplate, List<E>) -> Unit,
    ) {
        val secondary = route.secondary ?: return
        val enqueueAll = {
            items.forEach { entity ->
                dualWriteCompensationService?.enqueueInsert(route, collectionName, entity as Any)
            }
        }
        MongoDualWriteSupport.submitSecondaryWrite(route, collectionName, enqueueAll) {
            action(secondary, items)
        }
    }

    /**
     * 写操作模板选择钩子。优先通过 [MongoRoutingRegistry] 按集合前缀路由，
     * 未命中时回退到子类的 [determineMongoTemplate]。
     * 注意：此方法不感知双写，仅返回主路径模板，供非 AbstractMongoDao 写方法的外部调用者使用。
     */
    open fun determineMongoTemplate(collectionName: String, context: Any? = null): MongoTemplate {
        val default = determineMongoTemplate()
        return routingRegistry?.resolveWriteRoute(collectionName, context, default)?.primary
            ?: default
    }

    open fun determineReadMongoTemplate(collectionName: String, context: Any? = null): MongoTemplate {
        val default = determineMongoTemplate()
        return routingRegistry?.resolveReadRoute(collectionName, context, default)?.template
            ?: default
    }

    /**
     * 僵尸副本写保护检查（§25.2.2 E-01）。
     * 在写操作入口检查：如果目标模板是 Default 实例，但 projectId 已迁出到 Heavy，
     * 则 fail-fast 抛出异常，禁止静默写入 Default 上的僵尸副本。
     *
     * @throws IllegalStateException 当检测到僵尸副本写入时
     */
    protected fun assertNotZombieReplica(
        collectionName: String,
        route: WriteRoute,
    ) {
        MongoDualWriteSupport.assertNotZombieReplica(
            route, collectionName, determineMongoTemplate(), routingRegistry,
        )
    }

    /**
     * NONE 整体迁移模式下的 matchedCount=0 静默风险拦截（§1.4.4a）。
     *
     * NONE 模式（如 artifact_oplog 整体迁移）的双写期，主路径是 Offload 专属实例，副路径是 Default。
     * 若 update/delete 时主路径命中但副路径数据不一致（如 Default 还未同步到对应 _id），
     * 副路径会静默返回 matchedCount=0，造成数据漂移。
     *
     * 注意：此处只对 NONE 模式 + 双写期生效，PROJECT 模式跳过（业务可能正常 update 不存在的文档）。
     *
     * @param matched 主路径是否实际命中
     */
    protected fun assertNoneModeMatched(
        collectionName: String,
        route: WriteRoute,
        query: Query,
        matched: Boolean,
        op: String,
    ) {
        // 主路径未命中本来就是业务正常路径（如 update 不存在的文档），不拦截
        if (!matched) return
        val reg = routingRegistry ?: return
        val ruleName = prefixFor(collectionName) ?: return
        if (!reg.isNoneRoutingMode(ruleName)) return
        // 仅在双写期记录指标（用于监控告警 §22 / §25.5#16）
        // 真正的副路径 matchedCount 在 submitSecondaryWrite 内异步执行，结果丢失。
        // 这里记录"主路径命中，副路径需要核对"的指标，由 SidecarVerifier 补齐对账。
        if (reg.isDualWrite(ruleName)) {
            routingMetrics?.recordNoneMatched()
            if (logger.isDebugEnabled) {
                logger.debug(
                    "NONE-mode dual-write {} on [{}] matched=true, query={}, " +
                        "副路径 matchedCount 需由 SidecarVerifier 对账确认",
                    op, collectionName, query.queryObject
                )
            }
        }
    }

    /**
     * 查找匹配 collectionName 的规则名。
     */
    private fun prefixFor(collectionName: String): String? =
        routingRegistry?.resolveRuleName(collectionName)

    abstract fun determineCollectionName(entity: E): String

    abstract fun determineCollectionName(query: Query): String

    abstract fun determineCollectionName(aggregation: Aggregation): String

    companion object {
        val logger: Logger = LoggerFactory.getLogger(AbstractMongoDao::class.java)

        const val ID = "_id"
    }
}