package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 双写副路径执行与僵尸副本写保护，供 [com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao]
 * 与 [com.tencent.bkrepo.common.metadata.routing.DefaultNodeMongoOperations] 共用。
 */
object MongoDualWriteSupport {

  private val logger = LoggerFactory.getLogger(MongoDualWriteSupport::class.java)

  internal val executor = ThreadPoolExecutor(
    2, 16, 60L, TimeUnit.SECONDS,
    ArrayBlockingQueue(1000),
    { r -> Thread(r, "mongo-dual-write").apply { isDaemon = true } },
    RejectedExecutionHandler { _, exec ->
      logger.warn(
        "Dual-write executor queue full (active={}, queued={})",
        exec.activeCount,
        exec.queue.size,
      )
      throw RejectedExecutionException("Dual-write executor queue full")
    },
  )

  fun <T> executePrimaryWrite(
    route: WriteRoute,
    collectionName: String,
    defaultTemplate: MongoTemplate,
    registry: MongoRoutingRegistry?,
    action: (MongoTemplate) -> T,
  ): T {
    assertNotZombieReplica(route, collectionName, defaultTemplate, registry)
    return try {
      action(route.primary)
    } catch (exception: Exception) {
      val fallback = route.fallbackTemplate
      if (route.fallbackToDefault && fallback != null && fallback !== route.primary) {
        logger.warn(
          "WRITE FALLBACK to Default for [{}]: rule={} reason={}",
          collectionName, route.ruleName, exception.message, exception,
        )
        action(fallback)
      } else {
        throw exception
      }
    }
  }

  fun submitSecondaryWrite(
    route: WriteRoute,
    collectionName: String,
    enqueue: () -> Unit,
    action: (MongoTemplate) -> Unit,
  ) {
    val secondary = route.secondary ?: return
    val runSecondary = {
      runCatching { action(secondary) }
        .onFailure {
          logger.error("Dual-write secondary write failed [{}]: {}", collectionName, it.message)
          enqueue()
        }
    }
    if (route.syncSecondaryWrite) {
      runSecondary()
      return
    }
    try {
      executor.execute { runSecondary() }
    } catch (exception: RejectedExecutionException) {
      logger.warn("Dual-write executor rejected [{}], enqueue compensation", collectionName)
      enqueue()
    }
  }

  fun assertNotZombieReplica(
    route: WriteRoute,
    collectionName: String,
    defaultTemplate: MongoTemplate,
    registry: MongoRoutingRegistry?,
  ) {
    val reg = registry ?: return
    if (route.primary !== defaultTemplate) return
    val ruleName = reg.resolveRuleName(collectionName) ?: return
    val projectId = route.routingKey ?: return
    if (reg.isProjectRoutedOut(ruleName, projectId)) {
      val msg = "WRITE_PROTECTION: Attempted to write zombie replica on Default. " +
        "projectId=$projectId, collection=$collectionName, rule=$ruleName. " +
        "This indicates a code bug or missing routing configuration."
      logger.error(msg)
      throw IllegalStateException(msg)
    }
  }
}
