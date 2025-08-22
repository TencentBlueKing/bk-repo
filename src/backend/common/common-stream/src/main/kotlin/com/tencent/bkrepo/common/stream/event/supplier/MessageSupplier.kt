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

package com.tencent.bkrepo.common.stream.event.supplier

import com.tencent.bkrepo.common.stream.constant.BinderType
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.integration.support.MessageBuilder
import org.springframework.messaging.Message
import org.springframework.scheduling.annotation.Async

open class MessageSupplier(
    private val bridge: StreamBridge
) {

    /**
     * 将消息数据发送到消息队列, 将需要依赖该消息的其余模块解耦开
     */
    @Async
    open fun <T> delegateToSupplier(
        data: T,
        key: String? = null,
        topic: String,
        tagKey: String? = null,
        binderType: BinderType = BinderType.PULSAR
    ) {
        val (sendTopic, message) = buildMessage(
            data = data,
            key = key,
            topic = topic,
            tagKey = tagKey,
            binderType = binderType
        )
        logger.debug("Will send message: $data to topic $sendTopic")
        bridge.send(sendTopic, message)
    }

    /**
     * 构造消息
     * @param data 消息内容
     * @param key 消息对应key
     * @param topic 消息对应发送topic
     * @param tagKey 消息对应tag
     */
    private fun <T> buildMessage(
        data: T,
        key: String? = null,
        topic: String,
        tagKey: String? = null,
        binderType: BinderType
    ): Pair<String, Message<T>> {
        val messageBuilder = MessageBuilder.withPayload(data)
        if (binderType == BinderType.PULSAR) {
            key?.let {
                // 设置key
                messageBuilder.setHeader("PULSAR_key", key)
            }
            tagKey?.let {
                // 设置tag *如果要使用 Tag 消息，需要在 Producer 侧禁用掉 batch*
                messageBuilder.setHeader(tagKey, "TAGS")
            }
        }
        return Pair(topic, messageBuilder.build())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MessageSupplier::class.java)
    }
}
