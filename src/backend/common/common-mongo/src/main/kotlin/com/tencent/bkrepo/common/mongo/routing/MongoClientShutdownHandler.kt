package com.tencent.bkrepo.common.mongo.routing

import com.mongodb.client.MongoClient
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** G-22：多 MongoClient 并发关闭，单实例超时不阻塞其余实例。 */
object MongoClientShutdownHandler {

  private val logger = LoggerFactory.getLogger(MongoClientShutdownHandler::class.java)

  fun closeAll(
    clients: Collection<MongoClient>,
    shutdownTimeout: Duration = Duration.ofSeconds(30),
    perInstanceTimeout: Duration = Duration.ofSeconds(5),
  ) {
    if (clients.isEmpty()) return
    val futures = clients.distinct().map { client ->
      CompletableFuture.runAsync {
        try {
          client.close()
        } catch (e: Exception) {
          logger.warn("Failed to gracefully close MongoClient: {}", e.message)
        }
      }.orTimeout(perInstanceTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .exceptionally { null }
    }
    try {
      CompletableFuture.allOf(*futures.toTypedArray())
        .get(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      val pending = futures.count { !it.isDone }
      logger.error(
        "MongoClient shutdown timed out after {}s, {} client(s) still closing",
        shutdownTimeout.seconds,
        pending,
      )
    } catch (e: Exception) {
      logger.warn("MongoClient shutdown interrupted: {}", e.message)
    }
  }
}
