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
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.fs.server.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.fs.server.JWT_CLAIMS_PERMIT
import com.tencent.bkrepo.fs.server.JWT_CLAIMS_REPOSITORY
import com.tencent.bkrepo.fs.server.filter.ArtifactContextFilterFunction
import com.tencent.bkrepo.fs.server.filter.ArtifactFileCleanupFilter
import com.tencent.bkrepo.fs.server.filter.AuthHandlerFilterFunction
import com.tencent.bkrepo.fs.server.handler.FileOperationsHandler
import com.tencent.bkrepo.fs.server.handler.LoginHandler
import com.tencent.bkrepo.fs.server.handler.NodeOperationsHandler
import com.tencent.bkrepo.fs.server.metrics.ServerMetrics
import com.tencent.bkrepo.fs.server.utils.SecurityManager
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
    private val serverMetrics: ServerMetrics,
    private val artifactContextFilterFunction: ArtifactContextFilterFunction,
    private val artifactFileCleanupFilter: ArtifactFileCleanupFilter,
    private val securityManager: SecurityManager,
    private val loginHandler: LoginHandler
) {
    fun router() = coRouter {
        loginRouter()
        requireAuth()
        contextInit()
        nodeRouter()
        readWriteRouter()
    }

    private fun CoRouterFunctionDsl.loginRouter() {
        "/login".nest {
            accept(APPLICATION_JSON).nest {
                POST("/{projectId}/{repoName}", loginHandler::login)
            }
        }
    }

    /**
     * 认证过滤器
     * */
    private fun CoRouterFunctionDsl.requireAuth() {
        filter(authHandlerFilterFunction::filter)
    }

    private fun CoRouterFunctionDsl.contextInit() {
        filter(artifactContextFilterFunction::filter)
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
        "/block/flush".nest {
            accept(APPLICATION_JSON).nest {
                requireWritePermission()
                PUT(DEFAULT_MAPPING_URI, fileOperationsHandler::flush)
            }
        }

        "/block/write-flush/{offset}".nest {
            accept(APPLICATION_JSON).nest {
                requireWritePermission()
                PUT(DEFAULT_MAPPING_URI, fileOperationsHandler::writeAndFlush)
            }
        }

        "/block/{offset}".nest {
            accept(APPLICATION_JSON).nest {
                requireWritePermission()
                filter(artifactFileCleanupFilter::filter)
                PUT(DEFAULT_MAPPING_URI, fileOperationsHandler::write)
                addUploadMetrics()
            }
        }
        accept(APPLICATION_OCTET_STREAM).nest {
            requireReadPermission()
            GET(DEFAULT_MAPPING_URI, fileOperationsHandler::read)
            addDownloadMetrics()
        }
    }

    private fun CoRouterFunctionDsl.addUploadMetrics() {
        filter { req, next ->
            try {
                serverMetrics.uploadingCount.incrementAndGet()
                next(req)
            } finally {
                serverMetrics.uploadingCount.decrementAndGet()
            }
        }
    }

    private fun CoRouterFunctionDsl.addDownloadMetrics() {
        filter { req, next ->
            try {
                serverMetrics.downloadingCount.incrementAndGet()
                next(req)
            } finally {
                serverMetrics.downloadingCount.decrementAndGet()
            }
        }
    }

    private fun CoRouterFunctionDsl.requireWritePermission() {
        checkPermission(PermissionAction.WRITE)
    }

    private fun CoRouterFunctionDsl.requireReadPermission() {
        checkPermission(PermissionAction.READ)
    }

    private fun CoRouterFunctionDsl.checkPermission(action: PermissionAction) {
        filter { req, next ->
            val token = req.headers().header(HttpHeaders.AUTHORIZATION).firstOrNull()
            if (token == null) {
                ServerResponse.status(HttpStatus.FORBIDDEN).buildAndAwait()
            } else {
                val jws = securityManager.validateToken(token)
                val repo = jws.body[JWT_CLAIMS_REPOSITORY]
                val permit = jws.body[JWT_CLAIMS_PERMIT].toString()
                val projectId = req.pathVariable(PROJECT_ID)
                val repoName = req.pathVariable(REPO_NAME)
                val requestRepo = "$projectId/$repoName"
                if (requestRepo == repo && checkAction(permit, action)) {
                    next(req)
                } else {
                    ServerResponse.status(HttpStatus.FORBIDDEN).buildAndAwait()
                }
            }
        }
    }

    private fun checkAction(permit: String, action: PermissionAction): Boolean {
        if (action == PermissionAction.READ) {
            return permit == PermissionAction.READ.name || permit == PermissionAction.WRITE.name
        }
        return permit == action.name
    }
}
