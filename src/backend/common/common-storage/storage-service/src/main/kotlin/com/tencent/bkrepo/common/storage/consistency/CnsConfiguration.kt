/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.consistency

import com.tencent.bkrepo.common.frpc.EventMessageConverter
import com.tencent.bkrepo.common.frpc.FileEventBus
import com.tencent.bkrepo.common.frpc.GcProcess
import com.tencent.bkrepo.common.frpc.MessageConverterFactory
import com.tencent.bkrepo.common.frpc.ServiceRegistrarProcess
import com.tencent.bkrepo.common.frpc.ServiceRegistry
import com.tencent.bkrepo.common.frpc.StorageServiceInstance
import com.tencent.bkrepo.common.frpc.TextEventMessageConverter
import com.tencent.bkrepo.common.frpc.event.EventBus
import com.tencent.bkrepo.common.storage.core.StorageProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 一致性相关配置
 * */
@Configuration
@ConditionalOnProperty("storage.cns.enabled")
class CnsConfiguration {

    @Bean
    fun eventBus(
        storageProperties: StorageProperties,
        messageConverter: EventMessageConverter<String>
    ): EventBus {
        with(storageProperties.cns) {
            return FileEventBus(logPath, delayMillis, messageConverter, gcTimeout)
        }
    }

    @Bean
    fun cnsService(
        eventBus: EventBus,
        storageProperties: StorageProperties,
        serviceRegistry: ServiceRegistry<StorageServiceInstance>
    ): CnsService {
        val fileCheckProcess = FileCheckProcess(eventBus, storageProperties.cns.writeTimeout)
        eventBus.register(fileCheckProcess)
        return CnsServiceImpl(serviceRegistry, fileCheckProcess)
    }

    @Bean
    fun serviceRegistry(eventBus: EventBus): ServiceRegistry<StorageServiceInstance> {
        return ServiceRegistrarProcess(eventBus).apply {
            eventBus.register(this)
        }
    }

    @Bean
    fun gcProccess(
        eventBus: EventBus,
        messageConverter: EventMessageConverter<String>,
        serviceRegistry: ServiceRegistry<StorageServiceInstance>,
        storageProperties: StorageProperties,
    ): GcProcess {
        require(eventBus is FileEventBus)
        with(storageProperties.cns) {
            return GcProcess(
                eventBus,
                messageConverter,
                maxLogSize.toBytes(),
                gcTimeout,
                serviceRegistry
            ).apply {
                eventBus.register(this)
            }
        }
    }

    @Bean
    fun messageConverter(): EventMessageConverter<String> {
        val messageConverter = MessageConverterFactory.createSupportAllEventMessageConverter()
        require(messageConverter is TextEventMessageConverter)
        messageConverter.registerEvent(FileEventType.FILE_CHECK.name, FileCheckEvent::class.java)
        messageConverter.registerEvent(FileEventType.FILE_CHECK_ACK.name, FileCheckAckEvent::class.java)
        return messageConverter
    }
}
