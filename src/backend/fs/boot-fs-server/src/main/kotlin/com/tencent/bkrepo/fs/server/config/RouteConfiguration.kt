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

import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.fs.server.constant.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.fs.server.filter.ArtifactFileCleanupFilterFunction
import com.tencent.bkrepo.fs.server.filter.AuthHandlerFilterFunction
import com.tencent.bkrepo.fs.server.filter.DevXAccessFilter
import com.tencent.bkrepo.fs.server.filter.PermissionFilterFunction
import com.tencent.bkrepo.fs.server.getOrNull
import com.tencent.bkrepo.fs.server.handler.ClientHandler
import com.tencent.bkrepo.fs.server.handler.FileOperationsHandler
import com.tencent.bkrepo.fs.server.handler.LoginHandler
import com.tencent.bkrepo.fs.server.handler.NodeOperationsHandler
import com.tencent.bkrepo.fs.server.handler.service.FsNodeHandler
import com.tencent.bkrepo.fs.server.metrics.ServerMetrics
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_OCTET_STREAM
import org.springframework.util.AntPathMatcher
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.util.pattern.PathPattern
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * 路由配置
 * */
class RouteConfiguration(
    private val nodeOperationsHandler: NodeOperationsHandler,
    private val fileOperationsHandler: FileOperationsHandler,
    private val loginHandler: LoginHandler,
    private val fsNodeHandler: FsNodeHandler,
    private val clientHandler: ClientHandler,
    private val authHandlerFilterFunction: AuthHandlerFilterFunction,
    private val serverMetrics: ServerMetrics,
    private val devXAccessFilter: DevXAccessFilter,
    private val permissionFilterFunction: PermissionFilterFunction,
    private val artifactFileCleanupFilterFunction: ArtifactFileCleanupFilterFunction
) {
    fun router() = coRouter {
        filter(authHandlerFilterFunction::filter)
        filter(devXAccessFilter::filter)
        before(RouteConfiguration::initArtifactContext)
        filter(permissionFilterFunction::filter)
        POST("/login/{projectId}/{repoName}", loginHandler::login)
        POST("/devx/login/{repoName}", loginHandler::devxLogin)
        POST("/ioa/login/{projectId}/{repoName}", loginHandler::ioaLogin)
        POST("/ioa/ticket", loginHandler::ioaTicket)
        POST("/token/refresh/{projectId}/{repoName}", loginHandler::refresh)

        "/service/block".nest {
            GET("/list$DEFAULT_MAPPING_URI", fsNodeHandler::listBlocks)
        }

        "/node".nest {
            PUT("/change/attribute$DEFAULT_MAPPING_URI", nodeOperationsHandler::changeAttribute)
            PUT("/move$DEFAULT_MAPPING_URI", nodeOperationsHandler::move)
            POST("/create$DEFAULT_MAPPING_URI", nodeOperationsHandler::createNode)
            DELETE("/delete$DEFAULT_MAPPING_URI", nodeOperationsHandler::deleteNode)
            POST("/mkdir$DEFAULT_MAPPING_URI", nodeOperationsHandler::mkdir)
            POST("/mknod$DEFAULT_MAPPING_URI", nodeOperationsHandler::mknod)
            POST("/symlink$DEFAULT_MAPPING_URI", nodeOperationsHandler::symlink)
            PUT("/set-length$DEFAULT_MAPPING_URI", nodeOperationsHandler::setLength)
            GET("/stat$DEFAULT_MAPPING_URI", nodeOperationsHandler::getStat)
            GET("/info$DEFAULT_MAPPING_URI", nodeOperationsHandler::info)
            GET("/page$DEFAULT_MAPPING_URI", nodeOperationsHandler::listNodes)
            GET(DEFAULT_MAPPING_URI, nodeOperationsHandler::getNode)
        }

        PUT("/block/flush$DEFAULT_MAPPING_URI", fileOperationsHandler::flush)
        "/block".nest {
            filter(artifactFileCleanupFilterFunction::filter)
            PUT("/write-flush/{offset}$DEFAULT_MAPPING_URI", fileOperationsHandler::writeAndFlush)
            PUT("/{offset}$DEFAULT_MAPPING_URI", fileOperationsHandler::write)
            addMetrics(serverMetrics.uploadingCount)
        }

        "/client".nest {
            POST("/create/{projectId}/{repoName}", clientHandler::createClient)
            DELETE("/delete/{projectId}/{repoName}/{clientId}", clientHandler::removeClient)
            POST("/heartbeat/{projectId}/{repoName}/{clientId}", clientHandler::heartbeat)
        }

        "/service/client".nest {
            GET("/list", clientHandler::listClients)
        }

        accept(APPLICATION_OCTET_STREAM).nest {
            GET(DEFAULT_MAPPING_URI, fileOperationsHandler::read)
            addMetrics(serverMetrics.downloadingCount)
        }
    }

    private fun CoRouterFunctionDsl.addMetrics(metric: AtomicInteger) {
        filter { req, next ->
            try {
                metric.incrementAndGet()
                next(req)
            } catch (e: CancellationException) {
                val principal = req.exchange().attributes[USER_KEY]
                val clientAddress = req.remoteAddress().getOrNull()
                logger.info("Remote user[$principal],ip[$clientAddress] close connection.")
                throw e
            } finally {
                metric.decrementAndGet()
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RouteConfiguration::class.java)
        private val antPathMatcher = AntPathMatcher()
        private fun initArtifactContext(request: ServerRequest): ServerRequest {
            try {
                val projectId = request.pathVariable(PROJECT_ID)
                val repoName = request.pathVariable(REPO_NAME)
                val encodeUrl = AntPathMatcher.DEFAULT_PATH_SEPARATOR + antPathMatcher.extractPathWithinPattern(
                    (request.attribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
                        .get() as PathPattern).patternString,
                    request.path()
                )
                val decodeUrl = URLDecoder.decode(encodeUrl, StandardCharsets.UTF_8.name())
                val artifactUri = PathUtils.normalizeFullPath(decodeUrl)
                request.exchange().attributes[PROJECT_ID] = projectId
                request.exchange().attributes[REPO_NAME] = repoName
                val artifactInfo = ArtifactInfo(
                    projectId = projectId,
                    repoName = repoName,
                    artifactUri = artifactUri
                )
                request.exchange().attributes[ARTIFACT_INFO_KEY] = artifactInfo
                return request
            } catch (ignore: IllegalArgumentException) {
                return request
            }
        }
    }
}
