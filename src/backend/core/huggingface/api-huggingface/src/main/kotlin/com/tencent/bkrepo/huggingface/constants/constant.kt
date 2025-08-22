/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.constants

const val REPO_TYPE_MODEL = "model"
const val REPO_TYPE_DATASET = "dataset"

const val ERROR_CODE_HEADER = "X-Error-Code"
const val ERROR_MSG_HEADER = "X-Error-Message"

const val COMMIT_ID_HEADER = "X-Repo-Commit"

const val COMMIT_OP_HEADER = "header"
const val COMMIT_OP_FILE = "file"
const val COMMIT_OP_LFS = "lfsFile"
const val COMMIT_OP_DEL_FILE = "deletedFile"
const val COMMIT_OP_DEL_FOLDER = "deletedFolder"

const val REGULAR_UPLOAD_MODE = "regular"
const val LFS_UPLOAD_MODE = "lfs"

const val ORGANIZATION_KEY = "organization"
const val NAME_KEY = "name"
const val REVISION_KEY = "revision"
const val TYPE_KEY = "type"

const val PACKAGE_KEY = "packageKey"
const val VERSION = "version"

const val BASE64_ENCODING = "base64"
const val REVISION_MAIN = "main"
