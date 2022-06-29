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

package com.tencent.bkrepo.replication.constant

const val DEFAULT_VERSION = "1.0.0"

/**
 * external集群仓库名生成规则, <projectId>-<repoName>-<name>
 */
const val EXTERNAL_CLUSTER_NAME = "%s-%s-%s"
const val BEARER_REALM = "Bearer realm"
const val SERVICE = "service"
const val SCOPE = "scope"
const val REPOSITORY = "repository"
const val URL = "url"
const val HEADERS = "headers"
const val METHOD = "method"
const val HEAD_METHOD = "head"
const val GET_METHOD = "get"
const val POST_METHOD = "post"
const val PATCH_METHOD = "patch"
const val PUT_METHOD = "put"
const val BODY = "body"
const val PARAMS = "params"
const val LOCATION = "Location"
const val TOKEN = "token"

const val USERNAME = "username"
const val PASSWORD = "password"
const val CERTIFICATE = "certificate"

const val DOCKER_MANIFEST_JSON_FULL_PATH = "/%s/%s/manifest.json"
const val DOCKER_LAYER_FULL_PATH = "/%s/%s/%s"
const val OCI_MANIFEST_JSON_FULL_PATH = "/%s/manifest/%s/manifest.json"
const val OCI_LAYER_FULL_PATH = "/%s/blobs/%s"
