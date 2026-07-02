package com.tencent.bkrepo.common.mongo.observability

import io.micrometer.common.KeyValues
import org.springframework.data.mongodb.observability.MongoHandlerContext
import org.springframework.data.mongodb.observability.MongoHandlerObservationConvention
import org.springframework.data.mongodb.observability.MongoObservation.LowCardinalityCommandKeyNames
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
            LowCardinalityCommandKeyNames.DB_SYSTEM.withValue("mongodb"),
            LowCardinalityCommandKeyNames.MONGODB_COMMAND.withValue(context.commandName),
        )

        val connectionString = context.connectionString
        if (connectionString != null) {
            keyValues = keyValues.and(
                LowCardinalityCommandKeyNames.DB_CONNECTION_STRING.withValue(connectionString.connectionString),
            )
            val user = connectionString.username
            if (!ObjectUtils.isEmpty(user)) {
                keyValues = keyValues.and(LowCardinalityCommandKeyNames.DB_USER.withValue(user))
            }
        }

        if (!ObjectUtils.isEmpty(context.databaseName)) {
            keyValues = keyValues.and(LowCardinalityCommandKeyNames.DB_NAME.withValue(context.databaseName))
        }

        if (!ObjectUtils.isEmpty(context.collectionName)) {
            keyValues = keyValues.and(
                LowCardinalityCommandKeyNames.MONGODB_COLLECTION.withValue(context.collectionName),
            )
        }

        val connectionDescription = context.commandStartedEvent.connectionDescription ?: return keyValues
        val serverAddress = connectionDescription.serverAddress
        if (serverAddress != null) {
            keyValues = keyValues.and(
                LowCardinalityCommandKeyNames.NET_TRANSPORT.withValue("IP.TCP"),
                LowCardinalityCommandKeyNames.NET_PEER_NAME.withValue(serverAddress.host),
                LowCardinalityCommandKeyNames.NET_PEER_PORT.withValue(serverAddress.port.toString()),
            )
            val socketAddress = MongoCompatibilityAdapter.serverAddressAdapter(serverAddress).socketAddress
            if (socketAddress != null) {
                keyValues = keyValues.and(
                    LowCardinalityCommandKeyNames.NET_SOCK_PEER_ADDR.withValue(socketAddress.hostName),
                    LowCardinalityCommandKeyNames.NET_SOCK_PEER_PORT.withValue(socketAddress.port.toString()),
                )
            }
        }

        val connectionId = connectionDescription.connectionId
        if (connectionId != null) {
            keyValues = keyValues.and(
                LowCardinalityCommandKeyNames.MONGODB_CLUSTER_ID.withValue(
                    connectionId.serverId.clusterId.value,
                ),
            )
        }

        return keyValues
    }

    override fun getHighCardinalityKeyValues(context: MongoHandlerContext): KeyValues = KeyValues.empty()

    companion object {
        const val SPAN_NAME = "mongodb.command"
    }
}
