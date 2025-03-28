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

package com.tencent.bkrepo.cargo.utils

import com.tencent.bkrepo.cargo.constants.CARGO_INDEX_PREFIX
import com.tencent.bkrepo.cargo.constants.CARGO_JSON_PREFIX
import com.tencent.bkrepo.cargo.constants.CARGO_JSON_SUFFIX
import com.tencent.bkrepo.cargo.constants.CARGO_NODE_PREFIX
import com.tencent.bkrepo.cargo.constants.CARGO_NODE_SUFFIX
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode

object CargoUtils {

    fun getCargoFileFullPath(name: String, version: String): String {
        return CARGO_NODE_PREFIX + name + StringPool.SLASH + getCrateFileName(name, version, CARGO_NODE_SUFFIX)
    }

    fun getCargoJsonFullPath(name: String, version: String): String {
        return CARGO_JSON_PREFIX + name + StringPool.SLASH + getCrateFileName(name, version, CARGO_JSON_SUFFIX)
    }

    fun getCargoIndexFullPath(name: String): String {
        return CARGO_INDEX_PREFIX + createDirectory(name) + StringPool.SLASH + name
    }

    /**
     * Packages with 1 character names are placed in a directory named 1.
     * Packages with 2 character names are placed in a directory named 2.
     * Packages with 3 character names are placed in the directory 3/{first-character where {first-character} is the
     * first character of the package name.
     * All other packages are stored in directories named {first-two}/{second-two} where the top directory is the first
     * two characters of the package name, and the next subdirectory is the third and fourth characters of the package
     * name. For example, cargo would be stored in a file named ca/rg/cargo.
     */
    private fun createDirectory(name: String): String {
        return when (name.length) {
            1 -> "1"
            2 -> "2"
            3 -> "3/" + name.first().toString()
            4 -> name.substring(0, 2) + StringPool.SLASH + name.substring(3, 5)
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name)
        }
    }

    private fun getCrateFileName(name: String, version: String, suffix: String): String {
        return "%s-%s%s".format(name, version, suffix)
    }

}
