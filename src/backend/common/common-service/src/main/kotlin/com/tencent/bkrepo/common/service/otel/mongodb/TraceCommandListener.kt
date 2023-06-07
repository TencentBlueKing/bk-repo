/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.service.otel.mongodb

import com.mongodb.event.CommandFailedEvent
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.CommandSucceededEvent
import org.bson.BsonDocument
import org.bson.BsonValue
import org.springframework.cloud.sleuth.Span
import org.springframework.cloud.sleuth.Tracer
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class TraceCommandListener(
    private val tracer: Tracer,
    private val customizers: List<TraceMongoSpanCustomizer>
) : CommandListener {

    private val requestContext = ConcurrentHashMap<Int, Span>()

    override fun commandStarted(event: CommandStartedEvent) {
        val databaseName = event.databaseName
        if ("admin" == databaseName) {
            return
        }

        val parent = tracer.currentSpan()
        if (parent == null) {
            return
        }
        val childSpanBuilder = tracer.spanBuilder()
        childSpanBuilder.setParent(parent.context())

        val commandName = event.commandName
        val command = event.command
        val collectionName = getCollectionName(command, commandName)

        childSpanBuilder.name(getSpanName(commandName, collectionName))
            .kind(Span.Kind.CLIENT)

        if (collectionName != null) {
            childSpanBuilder.tag("db.mongodb.collection", collectionName)
        }

        customizers.forEach(Consumer { customizer: TraceMongoSpanCustomizer ->
            customizer.customizeCommandStartSpan(
                event,
                childSpanBuilder
            )
        })

        val childSpan = childSpanBuilder.start()
        requestContext[event.requestId] = childSpan
    }

    override fun commandSucceeded(event: CommandSucceededEvent) {
        val span = requestContext[event.requestId] ?: return
        span.end()
        requestContext.remove(event.requestId)
    }

    override fun commandFailed(event: CommandFailedEvent) {
        val span = requestContext[event.requestId] ?: return
        span.error(event.throwable)
        span.end()
        requestContext.remove(event.requestId)
    }

    private fun getCollectionName(command: BsonDocument, commandName: String): String? {
        if (COMMANDS_WITH_COLLECTION_NAME.contains(commandName)) {
            val collectionName = getNonEmptyBsonString(command[commandName])
            if (collectionName != null) {
                return collectionName
            }
        }
        // 其他的一些命令，例如getMore，包含字段 {"collection": collectionName}
        return getNonEmptyBsonString(command["collection"])
    }

    private fun getNonEmptyBsonString(bsonValue: BsonValue?): String? {
        if (bsonValue == null || !bsonValue.isString) {
            return null
        }
        val stringValue = bsonValue.asString().value.trim { it <= ' ' }
        return stringValue.ifEmpty { null }
    }

    private fun getSpanName(commandName: String, collectionName: String?): String {
        return if (collectionName == null) {
            commandName
        } else {
            "$collectionName.$commandName"
        }
    }

    companion object {
        val COMMANDS_WITH_COLLECTION_NAME = setOf(
            "aggregate", "count", "distinct", "mapReduce", "geoSearch", "delete", "find", "findAndModify",
            "insert", "update", "collMod", "compact", "convertToCapped", "create", "createIndexes", "drop",
            "dropIndexes", "killCursors", "listIndexes", "reIndex"
        )
    }
}
