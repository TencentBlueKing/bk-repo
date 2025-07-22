/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.webhook.service

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.webhook.config.WebHookProperties
import com.tencent.bkrepo.webhook.dao.WebHookDao
import com.tencent.bkrepo.webhook.dao.WebHookLogDao
import com.tencent.bkrepo.webhook.executor.WebHookExecutor
import com.tencent.bkrepo.webhook.metrics.WebHookMetrics
import com.tencent.bkrepo.webhook.payload.EventPayloadFactory
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

@Import(
    WebHookDao::class,
    WebHookLogDao::class,
    WebHookExecutor::class,
    EventPayloadFactory::class,
    WebHookProperties::class,
    WebHookMetrics::class,
    ServicePermissionClient::class,
    ServiceUserClient::class
)
@ComponentScan("com.tencent.bkrepo.webhook.service")
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
open class ServiceBaseTest {

    @MockitoBean
    lateinit var permissionManager: PermissionManager


    @MockitoBean
    lateinit var servicePermissionClient: ServicePermissionClient

    fun initMock() {
        whenever(servicePermissionClient.checkPermission(any())).thenReturn(null)
    }
}
