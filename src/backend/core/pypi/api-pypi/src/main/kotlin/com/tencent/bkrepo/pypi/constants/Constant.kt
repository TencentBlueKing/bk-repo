/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.pypi.constants

const val REPO_TYPE = "PYPI"

const val METADATA = "metadata"
const val FIELD_NAME = "Name"
const val FIELD_VERSION = "Version"
const val NAME = "name"
const val VERSION = "version"
const val SUMMARY = "summary"

const val REQUIRES_PYTHON = "requires_python"

const val LINE_BREAK = "<br />"
const val INDENT = "    "
const val HTML_ENCODED_LESS_THAN = "&lt;"
const val HTML_ENCODED_GREATER_THAN = "&gt;"
const val REQUIRES_PYTHON_ATTR = "data-requires-python"

const val NON_ALPHANUMERIC_SEQ_REGEX = "[-_.]+"

const val PACKAGE_INDEX_TITLE = "Simple Index"
const val VERSION_INDEX_TITLE = "Links for %s"
const val SIMPLE_PAGE_CONTENT =
"""<!DOCTYPE html>
<html>
  <head>
    <meta name="pypi:repository-version" content="1.0">
    <title>%s</title>
  </head>
  <body>
    <h1>%s</h1>
%s
  </body>
</html>"""

const val REMOTE_HTML_CACHE_FULL_PATH = "remoteHtml.html"
const val FLUSH_CACHE_EXPIRE = 60 * 24
const val XML_RPC_URI = "RPC2"
const val XML_RPC_OPERATION_OR = "or"

const val DISABLE_REPO_INDEX = "disableRepoIndex"
