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

package com.tencent.bkrepo.common.ratelimiter.utils

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import java.net.URI
import java.net.URISyntaxException

object ResourcePathUtils {

    /**
     * 分割路径， 支持带template变量路径
     */
    @Throws(InvalidResourceException::class)
    fun tokenizeResourcePath(resourcePath: String): List<String> {
        if (resourcePath.isBlank()) {
            return emptyList()
        }
        if (!resourcePath.startsWith("/")) {
            throw InvalidResourceException("invalid resource path: $resourcePath")
        }
        val dirs = resourcePath.split("/").toTypedArray()
        val dirList: MutableList<String> = ArrayList()
        for (i in dirs.indices) {
            if (dirs[i].contains("?")
                && (!dirs[i].startsWith("{") || !dirs[i].endsWith("}"))) {
                throw InvalidResourceException("invalid resource path: $resourcePath")
            }
            if (dirs[i].isNotEmpty()) {
                dirList.add(dirs[i])
            }
        }
        return dirList
    }

    /**
     * 获取URL路径，不带host等信息
     */
    fun getPathOfUrl(url: String): String? {
        if (url.isBlank()) {
            return null
        }
        val uri: URI
        try {
            uri = URI(url)
        } catch (e: URISyntaxException) {
            throw InvalidResourceException(url)
        }
        val path = uri.path
        if (path.isNullOrEmpty()) {
            return "/"
        }
        return path
    }

    /**
     * 截取user和path
     */
    fun getUserAndPath(resource: String): Pair<String, String> {
        val index = resource.indexOfFirst { it == CharPool.COLON }
        if (index == -1) {
            throw InvalidResourceException(resource)
        }
        return Pair(resource.substring(0, index), resource.substring(index + 1))
    }

    /**
     * 根据userId和path生成对应配置格式
     */
    fun buildUserPath(userId: String, resource: String): String {
        return "$userId:$resource"
    }
}
