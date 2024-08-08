/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.cache.preload.ai

import io.milvus.client.MilvusClient
import io.milvus.client.MilvusServiceClient
import io.milvus.param.ConnectParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableConfigurationProperties(
    AiProperties::class,
    MilvusClientProperties::class
)
@ConditionalOnProperty("job.artifact-access-log-embedding.enabled")
class SpringAiConfiguration {

    @Bean
    fun httpEmbeddingModel(properties: AiProperties): EmbeddingModel {
        return HttpEmbeddingModel(properties)
    }

    @Bean
    fun milvusClient(properties: MilvusClientProperties): MilvusClient {
        with(properties) {
            val builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withUri(uri)
                .withConnectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .withKeepAliveTime(keepAliveTimeMs, TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(keepAliveTimeoutMs, TimeUnit.MILLISECONDS)
                .withRpcDeadline(rpcDeadlineMs, TimeUnit.MILLISECONDS)
                .withIdleTimeout(idleTimeoutMs, TimeUnit.MILLISECONDS)

            if (username.isNotEmpty() && password.isNotEmpty()) {
                builder.withAuthorization(username, password)
            }

            if (!token.isNullOrEmpty()) {
                builder.withToken(token)
            }

            return MilvusServiceClient(builder.build())
        }
    }
}
