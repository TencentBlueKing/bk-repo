/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.composer

const val INIT_PACKAGES = "{\n" +
    "    \"packages\": {},\n" +
    "    \"providers-lazy-url\": \"/p/%package%.json\",\n" +
    "    \"search\": \"/search.json?q=%query%&type=%type%\"\n" +
    "\n" +
    "}"
const val COMPOSER_JSON = "composer.json"
const val COMPOSER_VERSION_INIT = "{\n" +
    "    \"packages\":{\n" +
    "        \"%s\":{\n" +
    "        }\n" +
    "    }\n" +
    "}"
const val DELIMITER = "/"
// 对 "/p/%package%.json" 请求的前缀标识
const val PACKAGE_JSON_PREFIX = "/p/"
// 对 "%package%.json" 请求的后缀标识
const val PACKAGE_JSON_SUFFIX = ".json"
// 构件下载前缀标识
const val DIRECT_DISTS = "direct-dists"

const val METADATA_KEY_PACKAGE_KEY = "packageKey"
const val METADATA_KEY_VERSION = "version"
