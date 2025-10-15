/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.notify.config

import com.tencent.bkrepo.common.notify.api.bkci.BkciChannelCredential
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotChannelCredential
import com.tencent.bkrepo.common.notify.client.NotifyClient
import com.tencent.bkrepo.common.notify.client.bkci.BkciNotifyClient
import com.tencent.bkrepo.common.notify.client.weworkbot.WeworkBotClient
import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration(proxyBeanMethods = false)
class NotifyClientConfiguration {

    private val defaultNotifyHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECOND, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECOND, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT_SECOND, TimeUnit.SECONDS)
            .build()
    }

    @Bean(WeworkBotChannelCredential.TYPE)
    fun weworkBotClient(notifyProperties: NotifyProperties): NotifyClient {
        return WeworkBotClient(defaultNotifyHttpClient, notifyProperties)
    }

    @Bean(BkciChannelCredential.TYPE)
    fun bkciClient(notifyProperties: NotifyProperties): NotifyClient {
        return BkciNotifyClient(defaultNotifyHttpClient, notifyProperties)
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_SECOND = 5L
        private const val DEFAULT_READ_TIMEOUT_SECOND = 30L
        private const val DEFAULT_WRITE_TIMEOUT_SECOND = 30L
    }
}
