/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
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

package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.constants.NON_ALPHANUMERIC_SEQ_REGEX
import com.tencent.bkrepo.pypi.constants.SIMPLE_INDEX_CACHE_DIR
import com.tencent.bkrepo.pypi.constants.SIMPLE_INDEX_CACHE_PACKAGES_PREFIX

object PypiSimpleIndexUtils {

    private val nonAlphanumericSeqRegex = Regex(NON_ALPHANUMERIC_SEQ_REGEX)

    /**
     * PEP 503 包名规范化：连续 [-_.] 转为单个 `-`，并转小写
     */
    fun normalizePackageName(packageName: String): String {
        return packageName.replace(nonAlphanumericSeqRegex, "-").lowercase()
    }

    fun packageCacheFullPath(packageName: String): String {
        return "$SIMPLE_INDEX_CACHE_PACKAGES_PREFIX${normalizePackageName(packageName)}.html"
    }

    fun isSimpleIndexCacheFolder(name: String): Boolean {
        return name == SIMPLE_INDEX_CACHE_DIR
    }
}
