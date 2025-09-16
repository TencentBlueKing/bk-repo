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

package com.tencent.bkrepo.replication.constant

const val DEFAULT_VERSION = "1.0.0"

const val REPOSITORY = "repository"
const val URL = "url"

const val USERNAME = "username"
const val PASSWORD = "password"
const val CERTIFICATE = "certificate"

const val DOCKER_MANIFEST_JSON_FULL_PATH = "/%s/%s/manifest.json"
const val DOCKER_LAYER_FULL_PATH = "/%s/%s/%s"
const val OCI_MANIFEST_JSON_FULL_PATH = "/%s/manifest/%s/manifest.json"
const val OCI_MANIFEST_LIST = "list.manifest.json"
const val OCI_LAYER_FULL_PATH = "/%s/blobs/%s"
const val OCI_LAYER_FULL_PATH_V1 = "/%s/blobs/%s/%s"
const val BLOB_PATH_REFRESHED_KEY = "blobPathRefreshed"
const val NODE_FULL_PATH = "fullPath"
const val SIZE = "size"
const val REPOSITORY_INFO = "repo"
const val SHA256 = "sha256"
const val MD5 = "md5"
const val FILE = "file"
const val STORAGE_KEY = "storageKey"
const val CHUNKED_UPLOAD = "chunkedUpload"

const val PIPELINE_ID = "pipelineId"
const val BUILD_ID = "buildId"
const val TASK_ID = "taskId"
const val NAME = "name"

const val RETRY_COUNT = 2
const val DELAY_IN_SECONDS: Long = 1

const val FEDERATED = "federated"
const val FEDERATED_SOURCE = "federatedSource"

