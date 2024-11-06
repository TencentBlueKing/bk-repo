/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.conan.constant

/**
 * Ping 返回参数
 */
const val X_CONAN_SERVER_CAPABILITIES = "X-Conan-Server-Capabilities"

/**
 *  send the headers to see if it is possible to skip uploading the file, because it
 *  is already in the server
 */
const val X_CHECKSUM_DEPLOY = "X-Checksum-Deploy"
const val X_CHECKSUM_SHA1 = "X-Checksum-Sha1"
const val NAME = "name"
const val VERSION = "version"
const val USERNAME = "username"
const val CHANNEL = "channel"
const val PACKAGE_ID = "packageId"
const val REVISION = "revision"
const val PACKAGE_REVISION = "pRevision"
const val PATH = "path"
const val UPLOAD_PATH = "uploadPath"

const val DEFAULT_REVISION_V1 = "0"
const val CONAN_INFOS = "conanInfos"
const val UPLOAD_URL_PREFIX = "files"

const val CONANS_URL_TAG = "conans"

// Files
const val CONANFILE = "conanfile.py"
const val CONANFILE_TXT = "conanfile.txt"
const val CONAN_MANIFEST = "conanmanifest.txt"

// const val REVISIONS_FILE = "revisions.txt"
const val CONANINFO = "conaninfo.txt"
const val INDEX_JSON = "index.json"
const val PACKAGE_TGZ_NAME = "conan_package.tgz"
const val EXPORT_TGZ_NAME = "conan_export.tgz"
const val EXPORT_SOURCES_TGZ_NAME = "conan_sources.tgz"

// Directories
const val EXPORT_FOLDER = "export"
const val EXPORT_SRC_FOLDER = "export_source"
const val PACKAGES_FOLDER = "package"

// type
const val MD5 = "md5"
const val URL = "url"
