/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.node.NodeDetail

/**
 * IP段下载拦截器
 * 1.办公网IP段限制
 * 2.自定义IP段限制
 * 3.白名单用户不受1、2的限制
 */
class IpSegmentInterceptor(
    rules: Map<String, Any>,
    private val properties: DownloadInterceptorProperties
): DownloadInterceptor<Map<String, Any>, NodeDetail>(
    rules
) {
    override fun parseRule(): Map<String, Any> {
        val customIpSegment = rules[IP_SEGMENT] as? List<String> ?: emptyList()
        customIpSegment.forEach {
            IpUtils.parseCidr(it)
        }
        return rules
    }

    override fun matcher(artifact: NodeDetail, rule: Map<String, Any>): Boolean {
        val officeNetworkEnabled = rules[OFFICE_NETWORK] as? Boolean ?: true
        val customIpSegment = rules[IP_SEGMENT] as? List<String> ?: emptyList()
        val whitelistUser = rules[WHITELIST_USER] as? List<String> ?: emptyList()

        val userId = SecurityUtils.getUserId()
        if (whitelistUser.contains(userId)) {
            return true
        }

        val clientIp = HttpContextHolder.getClientAddressFromAttribute()
        val officeNetworkIpSegment = if (officeNetworkEnabled) properties.officeNetwork.whiteList else emptyList()
        val ipSegment = officeNetworkIpSegment.plus(customIpSegment)
        ipSegment.forEach {
            if (IpUtils.isInRange(clientIp, it)) {
                return true
            }
        }

        return false
    }

    companion object {
        private const val OFFICE_NETWORK = "officeNetwork"
        private const val IP_SEGMENT = "ipSegment"
        private const val WHITELIST_USER = "whitelistUser"
    }
}
