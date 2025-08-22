/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.nuget.constant

const val REPO_TYPE = "NUGET"

const val ID = "id"
const val PACKAGE_KEY = "packageKey"
const val VERSION = "version"
const val LOWER_VERSION = "lowerVersion"
const val UPPER_VERSION = "upperVersion"
const val PACKAGE = "package"
const val DEPENDENCY = "dependency"
const val TARGET_FRAMEWORKS = "targetFramework"
const val REFERENCE = "reference"
const val FRAMEWORKS = "frameworks"
const val GROUP = "group"
const val MANIFEST = "manifest"
const val NUSPEC = ".nuspec"
const val METADATA = "nuget_metadata"
const val REGISTRATION = "registration"
const val SEMVER2 = "semver2"
const val INDEX = "index.json"
const val PACKAGE_NAME = "packageName"

const val REMOTE_URL = "remote_url"
const val REGISTRATION_PATH = "registrationPath"
const val SEMVER2_ENDPOINT = "isSemver2Endpoint"
const val CACHE_CONTEXT = "cacheContext"
const val QUERY_TYPE = "queryType"

const val NUGET_V3_NOT_FOUND =
"""<?xml version="1.0" encoding="utf-8"?>
<Error>
    <Code>BlobNotFound</Code>
    <Message>
        The specified blob does not exist.
    </Message>
</Error>
"""
