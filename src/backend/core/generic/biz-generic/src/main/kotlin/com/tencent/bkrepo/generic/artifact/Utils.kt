/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.common.service.util.okhttp.PlatformAuthInterceptor
import com.tencent.bkrepo.generic.config.PlatformProperties
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDetail
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import java.net.Inet4Address
import java.net.InetAddress

/**
 * 替换规则中的projectId与repoName
 *
 * @param oldRule 需要替换的规则
 * @param projectId 新的projectId
 * @param repoName 新的repoName
 *
 * @return 替换后的rule
 */
fun replaceProjectIdAndRepo(oldRule: Rule, projectId: String, repoName: String): Rule {
    if (oldRule !is Rule.NestedRule || oldRule.relation != Rule.NestedRule.RelationType.AND) {
        throw ErrorCodeException(
            status = HttpStatus.BAD_REQUEST,
            messageCode = GenericMessageCode.ARTIFACT_SEARCH_FAILED,
            params = arrayOf("rule must be NestedRule and relation must be AND")
        )
    }

    val newRules = ArrayList<Rule>(oldRule.rules.size)
    for (rule in oldRule.rules) {
        if (rule is Rule.QueryRule &&
            rule.field != NodeDetail::projectId.name &&
            rule.field != NodeDetail::repoName.name
        ) {
            newRules.add(rule)
        }
    }
    newRules.add(Rule.QueryRule(NodeDetail::projectId.name, projectId))
    newRules.add(Rule.QueryRule(NodeDetail::repoName.name, repoName))
    return Rule.NestedRule(newRules)
}

/**
 * 创建认证拦截器，存在对应的平台账号时将使用平台认证，否则使用普通basic认证
 */
fun createAuthenticateInterceptor(
    configuration: RemoteConfiguration,
    platforms: List<PlatformProperties>
): Interceptor? {
    val username = configuration.credentials.username
    val password = configuration.credentials.password

    // basic认证
    if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
        return BasicAuthInterceptor(username, password)
    }

    // platform认证
    val url = configuration.url.toHttpUrl()
    platforms.firstOrNull { it.host == url.host || it.ip == url.host }?.let {
        return PlatformAuthInterceptor(it.accessKey, it.secretKey, SecurityUtils.getUserId())
    }

    return null
}

/**
 * 自定义dns解析，存在平台dns配置时将平台域名解析到指定ip
 */
fun createPlatformDns(platforms: List<PlatformProperties>) = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return platforms.firstOrNull { it.host == hostname && it.ip.isNotEmpty() }?.let {
            listOf(Inet4Address.getByName(it.ip))
        } ?: Dns.SYSTEM.lookup(hostname)
    }
}
