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

package com.tencent.bkrepo.fs.server.config

import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.fs.server.RepositoryCache
import com.tencent.bkrepo.fs.server.config.feign.ErrorCodeDecoder
import com.tencent.bkrepo.fs.server.filter.ActuatorAuthFilter
import com.tencent.bkrepo.fs.server.filter.ArtifactFileCleanupFilterFunction
import com.tencent.bkrepo.fs.server.filter.AuthHandlerFilterFunction
import com.tencent.bkrepo.fs.server.filter.DevXAccessFilter
import com.tencent.bkrepo.fs.server.filter.PermissionFilterFunction
import com.tencent.bkrepo.fs.server.filter.ReactiveRequestContextFilter
import com.tencent.bkrepo.fs.server.handler.ClientHandler
import com.tencent.bkrepo.fs.server.handler.FileOperationsHandler
import com.tencent.bkrepo.fs.server.handler.LoginHandler
import com.tencent.bkrepo.fs.server.handler.NodeOperationsHandler
import com.tencent.bkrepo.fs.server.handler.service.FsNodeHandler
import com.tencent.bkrepo.fs.server.metrics.ServerMetrics
import com.tencent.bkrepo.fs.server.service.BlockNodeServiceImpl
import com.tencent.bkrepo.fs.server.service.ClientService
import com.tencent.bkrepo.fs.server.service.FileNodeService
import com.tencent.bkrepo.fs.server.service.FileOperationService
import com.tencent.bkrepo.fs.server.service.PermissionService
import com.tencent.bkrepo.fs.server.storage.CoArtifactFileFactory
import com.tencent.bkrepo.fs.server.storage.CoStorageManager
import com.tencent.bkrepo.fs.server.utils.DevxWorkspaceUtils
import com.tencent.bkrepo.fs.server.utils.IoaUtils
import com.tencent.bkrepo.fs.server.utils.SecurityManager
import com.tencent.bkrepo.fs.server.utils.SpringContextUtils
import com.tencent.devops.service.config.ServiceProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.beans

val beans = beans {
    bean<ServiceProperties>()
    bean<NodeOperationsHandler>()
    bean<FileOperationsHandler>()
    bean<FsNodeHandler>()
    bean<LoginHandler>()
    bean<ClientHandler>()
    bean<PermissionService>()
    bean<AuthHandlerFilterFunction>()
    bean<RepositoryCache>()
    bean<ServerMetrics>()
    bean<ActuatorAuthFilter>()
    bean<GlobalExceptionHandler>()
    bean<BlockNodeServiceImpl>()
    bean<ReactiveRequestContextFilter>()
    bean<CoArtifactFileFactory>()
    bean<CoStorageManager>()
    bean<ArtifactFileCleanupFilterFunction>()
    bean<FileNodeService>()
    bean<FileOperationService>()
    bean<ClientService>()
    bean<SecurityManager>()
    bean<JwtAuthProperties>()
    bean<SpringContextUtils>()
    bean<NettyWebServerAccessLogCustomizer>()
    bean<DevXAccessFilter>()
    bean<DevXProperties>()
    bean<PermissionFilterFunction>()
    bean<ErrorCodeDecoder>()
    bean<DevxWorkspaceUtils>()
    bean<IoaUtils>()
    bean {
        RouteConfiguration(ref(), ref(), ref(), ref(), ref(), ref(), ref(), ref(), ref(), ref()).router()
    }
    bean {
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}

class BeansInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(applicationContext: GenericApplicationContext) {
        beans.initialize(applicationContext)
    }
}
