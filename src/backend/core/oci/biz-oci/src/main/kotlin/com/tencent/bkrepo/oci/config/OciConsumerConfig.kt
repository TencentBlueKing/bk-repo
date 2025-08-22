/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.oci.config

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.oci.listener.consumer.RemoteImageRepoEventConsumer
import com.tencent.bkrepo.oci.listener.consumer.ThirdPartyReplicationEventConsumer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import java.util.function.Consumer

@Configuration
class OciConsumerConfig {

    // 之前继承Consumer方式框架升级后会报错，https://github.com/spring-cloud/spring-cloud-stream/issues/2704
    @Bean("remoteOciRepo")
    fun remoteImageRepoEventConsumer(
        remoteImageRepoEventConsumer: RemoteImageRepoEventConsumer
    ): Consumer<Message<ArtifactEvent>> {
        return Consumer {
            remoteImageRepoEventConsumer.accept(it)
        }
    }

    @Bean("thirdPartyReplication")
    fun thirdPartyReplicationEventConsumer(
        thirdPartyReplicationEventConsumer: ThirdPartyReplicationEventConsumer
    ): Consumer<Message<ArtifactEvent>> {
        return Consumer {
            thirdPartyReplicationEventConsumer.accept(it)
        }
    }
}
