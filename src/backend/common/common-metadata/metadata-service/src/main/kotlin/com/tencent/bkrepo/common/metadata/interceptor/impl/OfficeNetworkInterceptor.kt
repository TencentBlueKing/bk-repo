/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.interceptor.impl

import com.tencent.bkrepo.common.api.util.IpUtils
import com.tencent.bkrepo.common.metadata.interceptor.DownloadInterceptor
import com.tencent.bkrepo.common.metadata.interceptor.config.DownloadInterceptorProperties
import com.tencent.bkrepo.common.service.util.HttpContextHolder

/**
 * 办公网下载拦截器，只允许白名单IP下载
 */
@Deprecated("已合并到IP段下载拦截器")
class OfficeNetworkInterceptor<A>(
    rules: Map<String, Any>,
    private val properties: DownloadInterceptorProperties
) : DownloadInterceptor<Map<String, Boolean>, A>(rules) {

    /**
     * 示例；
     * "rules": {
     *   "enabled": true
     * }
     */
    override fun parseRule(): Map<String, Boolean> {
        return rules.mapValues { it.value.toString().toBoolean() }
    }


    override fun matcher(artifact: A, rule: Map<String, Boolean>): Boolean {
        if (rule[ENABLED] == false) {
            return true
        }

        val cidrList = properties.officeNetwork.whiteList
        val clientIp = HttpContextHolder.getClientAddress()
        cidrList.forEach {
            if (IpUtils.isInRange(clientIp, it)) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val ENABLED = "enabled"
    }
}
