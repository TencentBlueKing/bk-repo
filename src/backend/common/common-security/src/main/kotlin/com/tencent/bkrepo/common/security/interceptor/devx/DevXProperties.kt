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

package com.tencent.bkrepo.common.security.interceptor.devx

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.Ordered
import java.time.Duration

/**
 * 云研发配置
 * */
@ConfigurationProperties("devx")
data class DevXProperties(
    /**
     * 是否开启云研发相关配置
     * */
    var enabled: Boolean = false,
    /**
     * apigw app code
     * */
    var appCode: String = "",
    /**
     * apigw app secret
     * */
    var appSecret: String = "",
    /**
     * 查询云研发工作空间的URL
     * */
    var workspaceUrl: String = "",
    /**
     * 缓存的项目ip过期时间
     */
    var cacheExpireTime: Duration = Duration.ofMinutes(1L),
    /**
     * 缓存的项目数量
     */
    var cacheSize: Long = 1000L,
    /**
     * 配置属于项目的CVM
     * key 为项目ip， value为CVM配置
     */
    var projectCvmWhiteList: Map<String, Set<String>> = emptyMap(),
    /**
     * 可以从任意来源访问的用户
     */
    var userWhiteList: Set<String> = emptySet(),
    /**
     * 访问受限的用户ID前缀
     */
    var restrictedUserPrefix: Set<String> = emptySet(),
    /**
     * 访问受限的用户ID后缀
     */
    var restrictedUserSuffix: Set<String> = emptySet(),
    /**
     * 请求来源区分header-name
     */
    var srcHeaderName: String? = null,
    /**
     * 请求来源区分header-value
     */
    var srcHeaderValues: List<String> = emptyList(),
    /**
     * devX拦截器优先级，
     * 如果需要取用户信息优先级需要比[com.tencent.bkrepo.common.security.http.core.HttpAuthInterceptor]拦截器低
     */
    var interceptorOrder: Int = Ordered.LOWEST_PRECEDENCE - 100,
    /**
     * 不应用devX拦截器的接口
     */
    var excludePatterns: List<String> = emptyList(),
    /**
     * 远程制品库集群url
     */
    var remoteBkRepoUrl: String = "",
    /**
     * 将host解析到指定ip，不指定时则使用默认dns
     */
    var remoteBkRepoIp: String = "",
    /**
     * 远程制品库集群平台账号
     */
    var remoteBkRepoAccessKey: String = "",
    /**
     * 远程制品库集群平台账号密钥
     */
    var remoteBkRepoSecretKey: String = "",
    /**
     * 使用的远程制品库集群用户身份
     */
    var remoteBkRepoUser: String = "",

    /**
     * 应用devX拦截器的接口
     */
    var includePatterns: List<String> = emptyList(),
)
