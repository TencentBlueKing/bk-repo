package com.tencent.bkrepo.common.mongo.observability

import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import org.springframework.data.mongodb.observability.MongoHandlerContext
import org.springframework.data.mongodb.observability.MongoHandlerObservationConvention
import org.springframework.data.mongodb.util.MongoCompatibilityAdapter
import org.springframework.util.ObjectUtils

/**
 * 使用固定 span name 的 MongoDB Observation convention，避免 Spring 默认的
 * `{collection}.{command}` 动态 contextual name 导致 trace 基数膨胀。
 *
 * collection、command 等动态信息仍通过低基数 attribute 传递，与
 * [org.springframework.data.mongodb.observability.DefaultMongoHandlerObservationConvention] 一致。
 */
class LowCardinalityMongoHandlerObservationConvention : MongoHandlerObservationConvention {

    override fun getContextualName(context: MongoHandlerContext): String = SPAN_NAME

    override fun getLowCardinalityKeyValues(context: MongoHandlerContext): KeyValues {
        var keyValues = KeyValues.of(
            KeyValue.of(DB_SYSTEM, "mongodb"),
            KeyValue.of(DB_OPERATION, context.commandName),
        )

        val connectionString = context.connectionString
        if (connectionString != null) {
            keyValues = keyValues.and(KeyValue.of(DB_CONNECTION_STRING, connectionString.connectionString))
            val user = connectionString.username
            if (!ObjectUtils.isEmpty(user)) {
                keyValues = keyValues.and(KeyValue.of(DB_USER, user))
            }
        }

        if (!ObjectUtils.isEmpty(context.databaseName)) {
            keyValues = keyValues.and(KeyValue.of(DB_NAME, context.databaseName))
        }

        if (!ObjectUtils.isEmpty(context.collectionName)) {
            keyValues = keyValues.and(KeyValue.of(DB_MONGODB_COLLECTION, context.collectionName))
        }

        val connectionDescription = context.commandStartedEvent.connectionDescription ?: return keyValues
        val serverAddress = connectionDescription.serverAddress
        if (serverAddress != null) {
            keyValues = keyValues.and(
                KeyValue.of(NET_TRANSPORT, "IP.TCP"),
                KeyValue.of(NET_PEER_NAME, serverAddress.host),
                KeyValue.of(NET_PEER_PORT, serverAddress.port.toString()),
            )
            val socketAddress = MongoCompatibilityAdapter.serverAddressAdapter(serverAddress).socketAddress
            if (socketAddress != null) {
                keyValues = keyValues.and(
                    KeyValue.of(NET_SOCK_PEER_ADDR, socketAddress.hostName),
                    KeyValue.of(NET_SOCK_PEER_PORT, socketAddress.port.toString()),
                )
            }
        }

        val connectionId = connectionDescription.connectionId
        if (connectionId != null) {
            keyValues = keyValues.and(
                KeyValue.of(MONGODB_CLUSTER_ID, connectionId.serverId.clusterId.value),
            )
        }

        return keyValues
    }

    override fun getHighCardinalityKeyValues(context: MongoHandlerContext): KeyValues = KeyValues.empty()

    companion object {
        const val SPAN_NAME = "mongodb.command"

        private const val DB_SYSTEM = "db.system"
        private const val DB_CONNECTION_STRING = "db.connection_string"
        private const val DB_USER = "db.user"
        private const val DB_NAME = "db.name"
        private const val DB_MONGODB_COLLECTION = "db.mongodb.collection"
        private const val DB_OPERATION = "db.operation"
        private const val NET_TRANSPORT = "net.transport"
        private const val NET_PEER_NAME = "net.peer.name"
        private const val NET_PEER_PORT = "net.peer.port"
        private const val NET_SOCK_PEER_ADDR = "net.sock.peer.addr"
        private const val NET_SOCK_PEER_PORT = "net.sock.peer.port"
        private const val MONGODB_CLUSTER_ID = "spring.data.mongodb.cluster_id"
    }
}
