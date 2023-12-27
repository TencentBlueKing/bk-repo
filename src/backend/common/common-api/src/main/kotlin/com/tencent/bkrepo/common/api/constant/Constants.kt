/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.api.constant

/**
 * 认证成功后username写入request attributes的key
 */
const val USER_KEY = "userId"

/**
 * 是否系统管理员
 */
const val ADMIN_USER = "admin"

/**
 * 认证成功后platform写入request attributes的key
 */
const val PLATFORM_KEY = "platformId"

/**
 * Oauth认证成功后scope写入request attributes的key
 */
const val AUTHORITIES_KEY = "authorities"

/**
 * 微服务调用请求标记key
 */
const val MS_REQUEST_KEY = "MSRequest"

/**
 * 匿名用户
 */
const val ANONYMOUS_USER = "anonymous"

/**
 * common logger name
 */
const val EXCEPTION_LOGGER_NAME = "ExceptionLogger"
const val JOB_LOGGER_NAME = "JobLogger"
const val ACCESS_LOGGER_NAME = "AccessLogger"

/**
 * default pagination parameter
 */
const val DEFAULT_PAGE_NUMBER = 1
const val DEFAULT_PAGE_SIZE = 20
const val TOTAL_RECORDS_INFINITY = -1L

/**
 * service name
 */
const val REPOSITORY_SERVICE_NAME = "\${service.prefix:}repository\${service.suffix:}"
const val AUTH_SERVICE_NAME = "\${service.prefix:}auth\${service.suffix:}"
const val REPLICATION_SERVICE_NAME = "\${service.prefix:}replication\${service.suffix:}"
const val SCANNER_SERVICE_NAME = "\${service.prefix:}analyst\${service.suffix:}"
const val ANALYSIS_EXECUTOR_SERVICE_NAME = "\${service.prefix:}analysis-executor\${service.suffix:}"
const val HELM_SERVICE_NAME = "\${service.prefix:}helm\${service.suffix:}"
const val OCI_SERVICE_NAME = "\${service.prefix:}docker\${service.suffix:}"
const val JOB_SERVICE_NAME = "\${service.prefix:}job\${service.suffix:}"
const val FS_SERVER_SERVICE_NAME = "\${service.prefix:}fs-server\${service.suffix:}"
const val MAVEN_SERVICE_NAME = "\${service.prefix:}maven\${service.suffix:}"
const val ARCHIVE_SERVICE_NAME = "\${service.prefix:}archive\${service.suffix:}"
const val OPDATA_SERVICE_NAME = "\${service.prefix:}opdata\${service.suffix:}"
const val GENERIC_SERVICE_NAME = "\${service.prefix:}generic\${service.suffix:}"

/**
 * 认证相关
 */
const val BASIC_AUTH_PREFIX = "Basic "
const val BASIC_AUTH_PROMPT = "Basic realm=\"Authentication Required\""
const val PLATFORM_AUTH_PREFIX = "Platform "
const val BEARER_AUTH_PREFIX = "Bearer "
const val AUTH_HEADER_UID = "X-BKREPO-UID"
const val OAUTH_AUTH_PREFIX = "Oauth "
const val TEMPORARY_TOKEN_AUTH_PREFIX = "Temporary "

/**
 * micro service header user id key
 */
const val MS_AUTH_HEADER_UID = "X-BKREPO-MS-UID"

const val MS_REQUEST_SRC_CLUSTER = "X-BKREPO-MS-CLUSTER"

/**
 * 验证是否允许下载时，写入request attributes的key
 */
const val CLIENT_ADDRESS = "clientAddress"
const val DOWNLOAD_SOURCE = "downloadSource"

/**
 * 用于标记访问来源，web或api
 */
const val HEADER_ACCESS_FROM = "X-BKREPO-ACCESS-FROM"

/**
 * 来源于API调用
 */
const val ACCESS_FROM_API = "api"

/**
 * 来源于浏览器访问
 */
const val ACCESS_FROM_WEB = "web"
