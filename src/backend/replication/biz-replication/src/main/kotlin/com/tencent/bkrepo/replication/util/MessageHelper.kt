package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import org.slf4j.LoggerFactory
import org.springframework.integration.IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK
import org.springframework.integration.acks.AcknowledgmentCallback
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import java.security.MessageDigest

/**
 * 消息工具类
 */
object MessageHelper {

    private val logger = LoggerFactory.getLogger(MessageHelper::class.java)


    /**
     * Kafka 消息的 offset + partition 组合标识
     */
    private const val KAFKA_OFFSET = "kafka_offset"
    private const val KAFKA_PARTITION = "kafka_partition"
    private const val KAFKA_TOPIC = "kafka_topic"

    /**
     * Pulsar 消息ID头
     */
    private const val PULSAR_MESSAGE_ID = "PULSAR_messageId"


    /**
     * 确认消息
     * 按优先级尝试不同的确认方式：
     * 1. Spring Integration通用ACKNOWLEDGMENT_CALLBACK（最标准，适用于所有Spring Cloud Stream binder）
     * 2. Kafka的ACKNOWLEDGMENT header（Kafka特定）
     * 3. 如果都不存在，记录警告但不抛出异常
     *
     * @param message 消息对象
     */
    fun acknowledge(message: Message<*>) {
        try {
            // 方式1: 优先使用Spring Integration的通用ACKNOWLEDGMENT_CALLBACK
            val acknowledgmentCallback = message.headers[ACKNOWLEDGMENT_CALLBACK] as? AcknowledgmentCallback
            if (acknowledgmentCallback != null) {
                acknowledgmentCallback.acknowledge()
                return
            }

            // 方式2: 尝试Kafka的ACKNOWLEDGMENT header
            val kafkaAcknowledgment = message.headers[KafkaHeaders.ACKNOWLEDGMENT] as? Acknowledgment
            if (kafkaAcknowledgment != null) {
                kafkaAcknowledgment.acknowledge()
                return
            }
        } catch (e: Exception) {
            // 忽略ack失败，避免影响业务处理
            logger.warn("Failed to acknowledge message", e)
        }
    }


    /**
     * 从消息中获取唯一标识
     *
     * 按以下优先级尝试获取：
     * 1. payload 的 eventId 属性（ArtifactEvent）
     * 2. Pulsar消息ID
     * 3. Kafka消息的 topic + partition + offset 组合
     * 4. 基于消息内容计算的哈希值
     *
     * @param message 消息对象
     * @return 消息唯一标识
     */
    fun getMessageId(message: Message<ArtifactEvent>): String {
        // 1. 尝试从 payload 获取 eventId（ArtifactEvent）
        getEventIdFromPayload(message.payload)?.let { return it }

        val headers = message.headers

        // 2. 尝试 Pulsar 消息ID
        headers[PULSAR_MESSAGE_ID]?.let { return it.toString() }

        // 3. 尝试 Kafka 消息标识（topic + partition + offset 组合保证唯一）
        val kafkaTopic = headers[KAFKA_TOPIC]
        val kafkaPartition = headers[KAFKA_PARTITION]
        val kafkaOffset = headers[KAFKA_OFFSET]
        if (kafkaTopic != null && kafkaPartition != null && kafkaOffset != null) {
            return "kafka:$kafkaTopic:$kafkaPartition:$kafkaOffset"
        }

        // 5. 基于消息内容生成哈希值作为后备方案
        return generateContentHash(message.payload)
    }

    /**
     * 从 payload 中获取 eventId 属性
     *
     * @param payload 消息载体
     * @return eventId 或 null
     */
    private fun getEventIdFromPayload(payload: ArtifactEvent?): String? {
        if (payload == null) return null
        return try {
            payload.eventId
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 基于消息内容生成哈希值
     * 注意：这是一个后备方案，建议在发送消息时设置唯一标识
     *
     * @param ArtifactEvent 消息对象
     * @return 内容哈希值
     */
    private fun generateContentHash(event: ArtifactEvent): String {
        return "hash:${sha256(event.toString())}"
    }

    /**
     * 计算字符串的 SHA-256 哈希值
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

