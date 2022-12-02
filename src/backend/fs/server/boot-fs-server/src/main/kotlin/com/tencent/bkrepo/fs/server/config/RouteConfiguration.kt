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

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.fs.server.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.fs.server.filter.AuthHandlerFilterFunction
import com.tencent.bkrepo.fs.server.handler.FileOperationsHandler
import com.tencent.bkrepo.fs.server.handler.NodeOperationsHandler
import com.tencent.bkrepo.fs.server.metrics.ServerMetrics
import com.tencent.bkrepo.fs.server.service.PermissionService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_OCTET_STREAM
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter

/**
 * 路由配置
 * */
class RouteConfiguration(
    private val nodeOperationsHandler: NodeOperationsHandler,
    private val fileOperationsHandler: FileOperationsHandler,
    private val authHandlerFilterFunction: AuthHandlerFilterFunction,
    private val permissionService: PermissionService,
    private val serverMetrics: ServerMetrics
) {
    fun router() = coRouter {
        requireAuth()
        nodeRouter()
        readWriteRouter()
    }

    /**
     * 认证过滤器
     * */
    private fun CoRouterFunctionDsl.requireAuth() {
        filter(authHandlerFilterFunction::filter)
    }

    /**
     * 节点路由
     * */
    private fun CoRouterFunctionDsl.nodeRouter() {
        "/api/node".nest {
            requireReadPermission()
            accept(APPLICATION_JSON).nest {
                GET("/page$DEFAULT_MAPPING_URI", nodeOperationsHandler::listNodes)
            }
            accept(APPLICATION_JSON).nest {
                GET(DEFAULT_MAPPING_URI, nodeOperationsHandler::getNode)
            }
        }
    }

    /**
     * 文件读写路由
     * */
    private fun CoRouterFunctionDsl.readWriteRouter() {
        accept(APPLICATION_OCTET_STREAM).nest {
            requireReadPermission()
            GET(DEFAULT_MAPPING_URI, fileOperationsHandler::download)
            filter { req, next ->
                try {
                    serverMetrics.downloadingCount.incrementAndGet()
                    next(req)
                } finally {
                    serverMetrics.downloadingCount.decrementAndGet()
                }
            }
        }
    }

    private fun CoRouterFunctionDsl.requireReadPermission() {
        checkPermission(PermissionAction.READ)
    }
    private fun CoRouterFunctionDsl.checkPermission(action: PermissionAction) {
        filter { req, next ->
            if (!permissionService.checkPermission(req, action)) {
                ServerResponse.status(HttpStatus.FORBIDDEN).buildAndAwait()
            } else {
                next(req)
            }
        }
    }
}
