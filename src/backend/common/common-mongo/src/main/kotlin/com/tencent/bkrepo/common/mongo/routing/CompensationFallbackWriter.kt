package com.tencent.bkrepo.common.mongo.routing

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

/**
 * 补偿入队失败时的本地文件兜底写入器（§25.2.3 E-15）。
 *
 * 当补偿队列的 MongoDB 不可写时，将补偿任务序列化为本地 JSON 文件，
 * 防止 Heavy/Default 数据永久不一致。
 *
 * 文件路径：/data/bkrepo/compensation_fallback/{rule}_{routingKey}_{timestamp}.json
 */
class CompensationFallbackWriter {

    private val fallbackDir: Path = Paths.get(DEFAULT_FALLBACK_DIR)

    init {
        runCatching {
            Files.createDirectories(fallbackDir)
        }.onFailure {
            logger.error("Failed to create compensation fallback directory: $fallbackDir", it)
        }
    }

    fun write(task: CompensationTaskSnapshot) {
        val filename = "${task.ruleName}_${task.routingKey ?: "null"}_${System.currentTimeMillis()}.json"
        val filePath = fallbackDir.resolve(filename)
        runCatching {
            Files.writeString(filePath, task.toJson())
            logger.info("Compensation fallback written to: $filePath")
        }.onFailure {
            logger.error("CRITICAL: Failed to write compensation fallback file: $filePath", it)
        }
    }

    /**
     * 列出所有回退文件。
     */
    fun list(): List<Path> = runCatching {
        Files.list(fallbackDir)
            .filter { it.toString().endsWith(".json") }
            .sorted()
            .toList()
    }.getOrDefault(emptyList())

    /**
     * 读取并删除回退文件（成功恢复后删除）。
     */
    fun readAndDelete(path: Path): CompensationTaskSnapshot? {
        return runCatching {
            val content = Files.readString(path)
            val snapshot = CompensationTaskSnapshot.fromJson(content)
            if (snapshot != null) {
                Files.deleteIfExists(path)
            }
            snapshot
        }.onFailure {
            logger.error("Failed to read compensation fallback file: $path", it)
        }.getOrNull()
    }

    companion object {
        private const val DEFAULT_FALLBACK_DIR = "/data/bkrepo/compensation_fallback"
        private val logger = LoggerFactory.getLogger(CompensationFallbackWriter::class.java)
    }
}

/**
 * 补偿任务快照，用于本地文件序列化。
 */
data class CompensationTaskSnapshot(
    val ruleName: String?,
    val routingKey: String?,
    val collectionName: String,
    val operationType: String,
    val targetUseDefault: Boolean,
    val targetInstance: String?,
    val entityClass: String?,
    val entityDocument: Map<String, Any?>?,
    val queryDocument: Map<String, Any?>?,
    val updateDocument: Map<String, Any?>?,
    val optionsDocument: Map<String, Any?>?,
    val createdAt: String,
) {
    companion object {
        private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
            .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())

        fun toJson(snapshot: CompensationTaskSnapshot): String =
            objectMapper.writeValueAsString(snapshot)

        fun fromJson(json: String): CompensationTaskSnapshot? =
            runCatching { objectMapper.readValue(json, CompensationTaskSnapshot::class.java) }.getOrNull()
    }
}

fun CompensationTaskSnapshot.toJson(): String = CompensationTaskSnapshot.toJson(this)