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

package com.tencent.bkrepo.repository.constant

const val SYSTEM_USER = "system"
const val SHARDING_COUNT = 256
const val METADATA_PREFIX = "metadata."
const val DEFAULT_STORAGE_CREDENTIALS_KEY = "default"

const val PROJECT_ID = "projectId"
const val REPO_NAME = "repoName"
const val FULL_PATH = "fullPath"
const val NAME = "name"
const val METADATA = "metadata"
const val NODE_METADATA = "nodeMetadata"
const val MD5 = "md5"
const val SHA256 = "sha256"
const val PACKAGE_KEY = "key"
const val VERSION = "version"
const val CREATED_DATE = "createdDate"
const val ORDINAL = "ordinal"
const val PACKAGE_KEY_SEPARATOR = "://"

const val NODE_DETAIL_LIST_KEY = "nodeDetailList"
const val PROXY_DOWNLOAD_URL = "proxyDownloadUrl"
