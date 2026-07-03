/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.mongo.reactive.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.api.routing.DualWriteContext
import com.tencent.bkrepo.common.mongo.api.routing.DualWriteExecutor
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import com.tencent.bkrepo.common.mongo.reactive.routing.MongoReactiveRoutingRegistry
import com.tencent.bkrepo.common.mongo.reactive.routing.ReactiveWriteRoute
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteSupport
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.MongoCollectionUtils
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import reactor.core.publisher.Flux
import java.util.IdentityHashMap
import java.lang.reflect.ParameterizedType

abstract class AbstractMongoReactiveDao<E> : MongoReactiveDao<E> {

    @Suppress("LateinitUsage")
    @Autowired(required = false)
    private var reactiveRoutingRegistry: MongoReactiveRoutingRegistry? = null

    @Suppress("LateinitUsage")
    @Autowired(required = false)
    private var dualWriteCompensationService: MongoDualWriteCompensationService? = null

    @Autowired(required = false)
    private var dualWriteExecutor: DualWriteExecutor? = null

    @Suppress("UNCHECKED_CAST")
    protected open val classType = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<E>

    /**
     * 集合名称
     */
    protected open val collectionName: String by lazy { determineCollectionName() }

    suspend fun findOne(query: Query): E? {
        return findOne(query, classType)
    }

    suspend fun find(query: Query): List<E> {
        return find(query, classType)
    }

    suspend fun findAll(): List<E> {
        return findAll(classType)
    }

    suspend fun stream(query: Query): Flux<E> {
        return stream(query, classType)
    }

    override suspend fun <T> stream(query: Query, clazz: Class<T>): Flux<T> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao stream: [$query] [$clazz]")
        }
        val col = determineCollectionName(query)
        return readTemplate(col, query).find(query, clazz, col)
    }

    override suspend fun <T> find(query: Query, clazz: Class<T>): List<T> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao find: [$query] [$clazz]")
        }
        val col = determineCollectionName(query)
        return readTemplate(col, query)
            .find(query, clazz, col)
            .collectList().awaitSingle()
    }

    override suspend fun <T> findAll(clazz: Class<T>): List<T> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao findAll: [$clazz]")
        }
        return determineReactiveMongoOperations()
            .findAll(clazz, determineCollectionName())
            .collectList().awaitSingle()
    }

    override suspend fun updateFirst(query: Query, update: Update): UpdateResult {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao updateFirst: [$query], [$update]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        return runDualWrite(
            route = route,
            collectionName = col,
            context = query,
            enqueue = {
                dualWriteCompensationService?.enqueueUpdateFirst(
                    route.toCompensationRoute(),
                    col,
                    query,
                    update,
                )
            },
        ) {
            it.updateFirst(query, update, col).awaitSingle()
        }
    }

    override suspend fun updateMulti(query: Query, update: Update): UpdateResult {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao updateMulti: [$query], [$update]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        return runDualWrite(
            route = route,
            collectionName = col,
            context = query,
            enqueue = {
                dualWriteCompensationService?.enqueueUpdateMulti(
                    route.toCompensationRoute(),
                    col,
                    query,
                    update,
                )
            },
        ) {
            it.updateMulti(query, update, col).awaitSingle()
        }
    }

    override suspend fun <T> findOne(query: Query, clazz: Class<T>): T? {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao findOne: [$query] [$clazz]")
        }
        val col = determineCollectionName(query)
        return readTemplate(col, query)
            .findOne(query, clazz, col)
            .awaitSingleOrNull()
    }

    override suspend fun save(entity: E): E {
        val col = determineCollectionName(entity)
        val route = writeRoute(col, entity)
        return runDualWrite(
            route = route,
            collectionName = col,
            context = entity,
            enqueue = { dualWriteCompensationService?.enqueueSave(route.toCompensationRoute(), col, entity as Any) },
        ) {
            @Suppress("UNCHECKED_CAST")
            (it.save(entity as Any, col).awaitSingle() as E)
        }
    }

    override suspend fun insert(entity: E): E {
        val col = determineCollectionName(entity)
        val route = writeRoute(col, entity)
        return runDualWrite(
            route = route,
            collectionName = col,
            context = entity,
            enqueue = { dualWriteCompensationService?.enqueueInsert(route.toCompensationRoute(), col, entity as Any) },
        ) {
            @Suppress("UNCHECKED_CAST")
            (it.insert(entity as Any, col).awaitSingle() as E)
        }
    }

    override suspend fun insert(entityCollection: Collection<E>): Collection<E> {
        if (entityCollection.isEmpty()) return entityCollection
        val first = entityCollection.first()
        val grouped = entityCollection.groupBy { entity ->
            val col = determineCollectionName(entity)
            val route = writeRoute(col, entity)
            Triple(col, route.primary, route)
        }
        grouped.forEach { (key, batch) ->
            val (col, _, route) = key
            executePrimaryWrite(route, col, batch.first()) {
                it.insert(batch, col).collectList().awaitSingle()
            }
            submitSecondaryBatchWrite(route, col, batch)
        }
        return entityCollection
    }

    override suspend fun remove(query: Query): DeleteResult {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao remove: [$query]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        return runDualWrite(
            route = route,
            collectionName = col,
            context = query,
            enqueue = {
                dualWriteCompensationService?.enqueueRemove(
                    route.toCompensationRoute(),
                    col,
                    classType.name,
                    query,
                )
            },
        ) {
            it.remove(query, classType, col).awaitSingle()
        }
    }

    override suspend fun upsert(query: Query, update: Update): UpdateResult {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao upsert: [$query], [$update]")
        }
        val col = determineCollectionName(query)
        val route = writeRoute(col, query)
        return try {
            runDualWrite(
                route = route,
                collectionName = col,
                context = query,
                enqueue = {
                    dualWriteCompensationService?.enqueueUpsert(
                        route.toCompensationRoute(),
                        col,
                        query,
                        update,
                    )
                },
            ) {
                it.upsert(query, update, col).awaitSingle()
            }
        } catch (exception: DuplicateKeyException) {
            logger.warn("Upsert error[DuplicateKeyException]: " + exception.message.orEmpty())
            runDualWrite(
                route = route,
                collectionName = col,
                context = query,
                enqueue = {
                    dualWriteCompensationService?.enqueueUpsert(
                        route.toCompensationRoute(),
                        col,
                        query,
                        update,
                    )
                },
            ) {
                it.upsert(query, update, col).awaitSingle()
            }
        }
    }

    override suspend fun count(query: Query): Long {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao count: [$query]")
        }
        val col = determineCollectionName(query)
        return readTemplate(col, query).count(query, col).awaitSingle()
    }

    override suspend fun exists(query: Query): Boolean {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao exists: [$query]")
        }
        val col = determineCollectionName(query)
        return readTemplate(col, query).exists(query, col).awaitSingle()
    }

    override suspend fun <T> findAndModify(
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
        return runDualWrite(
            route = route,
            collectionName = col,
            context = query,
            enqueue = {
                dualWriteCompensationService?.enqueueFindAndModify(
                    route.toCompensationRoute(),
                    col,
                    query,
                    update,
                    options,
                    clazz.name,
                )
            },
        ) {
            it.findAndModify(query, update, options, clazz, col).awaitSingle()
        }
    }

    override suspend fun <O> aggregate(aggregation: Aggregation, outputType: Class<O>): MutableList<O> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo aggregate: [$aggregation]")
        }
        return determineReactiveMongoOperations().aggregate(
            aggregation,
            determineCollectionName(aggregation),
            outputType
        ).collectList().awaitSingle()
    }

    protected open fun determineCollectionName(): String {
        var collectionName: String? = null
        if (classType.isAnnotationPresent(Document::class.java)) {
            val document = classType.getAnnotation(Document::class.java)
            collectionName = if (document.collection.isNotBlank()) document.collection else document.value
        }

        return if (collectionName.isNullOrEmpty()) {
            MongoCollectionUtils.getPreferredCollectionName(classType)
        } else collectionName
    }

    abstract fun determineReactiveMongoOperations(): ReactiveMongoTemplate

    /**
     * 写操作模板选择钩子，优先通过 [MongoReactiveRoutingRegistry] 路由，未命中回退到子类实现。
     */
    open fun determineReactiveMongoOperations(collectionName: String, context: Any? = null): ReactiveMongoTemplate {
        val default = determineReactiveMongoOperations()
        val registry = reactiveRoutingRegistry ?: return default
        val route = registry.resolveWriteRoute(collectionName, context, default)
        registry.assertWriteNotZombie(route, collectionName, default)
        return route.primary
    }

    /**
     * 读操作模板选择钩子，优先使用 Secondary，未命中回退到 [determineReactiveMongoOperations]。
     */
    open fun determineReadReactiveMongoOperations(
        collectionName: String,
        context: Any? = null,
    ): ReactiveMongoTemplate = readTemplate(collectionName, context)

    private fun readTemplate(collectionName: String, context: Any?): ReactiveMongoTemplate {
        val default = determineReactiveMongoOperations()
        val registry = reactiveRoutingRegistry ?: return default
        val route = registry.resolveReadRoute(collectionName, context, default)
        return route.template
    }

    abstract fun determineCollectionName(query: Query): String

    abstract fun determineCollectionName(entity: E): String

    abstract fun determineCollectionName(aggregation: Aggregation): String

    private fun writeRoute(collectionName: String, context: Any?): ReactiveWriteRoute {
        val default = determineReactiveMongoOperations()
        val registry = reactiveRoutingRegistry ?: return ReactiveWriteRoute(default)
        return registry.resolveWriteRoute(collectionName, context, default)
    }

    private suspend fun <T> runDualWrite(
        route: ReactiveWriteRoute,
        collectionName: String,
        context: Any?,
        enqueue: () -> Unit,
        write: suspend (ReactiveMongoTemplate) -> T,
    ): T {
        val executor = dualWriteExecutor
        if (executor != null && route.secondary != null) {
            return executor.execute(
                context = route.toDualWriteContext(
                    collectionName = collectionName,
                    defaultTemplate = determineReactiveMongoOperations(),
                    enqueue = enqueue,
                ),
                primary = { runBlocking { executePrimaryWrite(route, collectionName, context, write) } },
                secondary = {
                    runBlocking { write(route.secondary!!) }
                    Unit
                },
            )
        }
        val result = executePrimaryWrite(route, collectionName, context, write)
        submitSecondaryWrite(route, collectionName, enqueue) {
            runBlocking { write(it) }
            Unit
        }
        return result
    }

    private suspend fun <T> executePrimaryWrite(
        route: ReactiveWriteRoute,
        collectionName: String,
        context: Any?,
        action: suspend (ReactiveMongoTemplate) -> T,
    ): T {
        val primary = determineReactiveMongoOperations(collectionName, context)
        return try {
            action(primary)
        } catch (exception: Exception) {
            val fallback = route.fallbackTemplate
            if (route.fallbackToDefault && fallback != null && fallback !== route.primary) {
                logger.warn(
                    "WRITE FALLBACK to Default for [{}]: rule={} reason={}",
                    collectionName,
                    route.ruleName,
                    exception.message,
                    exception,
                )
                action(determineReactiveMongoOperations())
            } else {
                throw exception
            }
        }
    }

    private fun submitSecondaryWrite(
        route: ReactiveWriteRoute,
        collectionName: String,
        enqueue: () -> Unit,
        action: (ReactiveMongoTemplate) -> Unit,
    ) {
        if (route.secondary == null) return
        MongoDualWriteSupport.submitSecondaryWrite(
            route = route.toWriteRoute(determineReactiveMongoOperations()),
            collectionName = collectionName,
            enqueue = enqueue,
        ) {
            action(route.secondary!!)
        }
    }

    private fun submitSecondaryBatchWrite(
        route: ReactiveWriteRoute,
        collectionName: String,
        items: List<E>,
    ) {
        if (route.secondary == null) return
        val enqueueAll = {
            items.forEach { entity ->
                dualWriteCompensationService?.enqueueInsert(
                    route.toCompensationRoute(),
                    collectionName,
                    entity as Any,
                )
            }
        }
        submitSecondaryWrite(route, collectionName, enqueueAll) {
            runBlocking { it.insert(items, collectionName).collectList().awaitSingle() }
        }
    }

    private fun ReactiveWriteRoute.toDualWriteContext(
        collectionName: String,
        defaultTemplate: ReactiveMongoTemplate,
        enqueue: () -> Unit,
    ): DualWriteContext {
        val tokens = tokenizedTemplates(defaultTemplate)
        return DualWriteContext(
            route = toWriteRoute(tokens),
            collectionName = collectionName,
            defaultTemplate = tokens.requireToken(defaultTemplate),
            enqueueOnFailure = enqueue,
        )
    }

    private fun ReactiveWriteRoute.toCompensationRoute(): WriteRoute =
        toWriteRoute(determineReactiveMongoOperations())

    private fun ReactiveWriteRoute.toWriteRoute(
        defaultTemplate: ReactiveMongoTemplate,
    ): WriteRoute = toWriteRoute(tokenizedTemplates(defaultTemplate))

    private fun ReactiveWriteRoute.toWriteRoute(
        tokens: IdentityHashMap<ReactiveMongoTemplate, MongoTemplate>,
    ): WriteRoute = WriteRoute(
        primary = tokens.requireToken(primary),
        secondary = secondary?.let { tokens.requireToken(it) },
        secondaryTarget = secondaryTarget,
        fallbackTemplate = fallbackTemplate?.let { tokens.requireToken(it) },
        fallbackToDefault = fallbackToDefault,
        routingKey = routingKey,
        ruleName = ruleName,
        isDefaultInstance = isDefaultInstance,
        syncSecondaryWrite = syncSecondaryWrite,
    )

    private fun ReactiveWriteRoute.tokenizedTemplates(
        defaultTemplate: ReactiveMongoTemplate,
    ): IdentityHashMap<ReactiveMongoTemplate, MongoTemplate> {
        val tokens = IdentityHashMap<ReactiveMongoTemplate, MongoTemplate>()
        fun register(template: ReactiveMongoTemplate?) {
            if (template != null && template !in tokens) {
                tokens[template] = MongoTemplate(DUAL_WRITE_TOKEN_DB_FACTORY)
            }
        }
        register(defaultTemplate)
        register(primary)
        register(secondary)
        register(fallbackTemplate)
        return tokens
    }

    private fun IdentityHashMap<ReactiveMongoTemplate, MongoTemplate>.requireToken(
        template: ReactiveMongoTemplate,
    ): MongoTemplate = get(template) ?: error("Missing dual-write bridge token")

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractMongoReactiveDao::class.java)
        private val DUAL_WRITE_TOKEN_DB_FACTORY =
            SimpleMongoClientDatabaseFactory("mongodb://localhost:27017/test")

        /**
         * mongodb 默认id字段
         */
        const val ID = "_id"
    }
}
