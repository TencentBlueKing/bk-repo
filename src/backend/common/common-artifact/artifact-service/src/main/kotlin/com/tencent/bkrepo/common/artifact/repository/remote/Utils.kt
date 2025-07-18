/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.repository.remote

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool.ROOT
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.NetworkProxyConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteCredentialsConfiguration
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.service.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.common.service.util.okhttp.TokenAuthInterceptor
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 创建代理
 */
fun createProxy(configuration: NetworkProxyConfiguration?): Proxy {
    return configuration?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress(it.host, it.port)) } ?: Proxy.NO_PROXY
}

/**
 * 创建代理身份认证
 */
fun createProxyAuthenticator(configuration: NetworkProxyConfiguration?): Authenticator {
    val username = configuration?.username
    val password = configuration?.password
    return if (username != null && password != null) {
        Authenticator { _, response ->
            response.request
                .newBuilder()
                .header(HttpHeaders.PROXY_AUTHORIZATION, Credentials.basic(username, password))
                .build()
        }
    } else Authenticator.NONE
}

/**
 * 创建身份认证拦截器
 */
fun createAuthenticateInterceptor(configuration: RemoteCredentialsConfiguration): Interceptor? {
    val username = configuration.username
    val password = configuration.password
    val credentialKey = configuration.credentialKey
    return if (username != null && password != null) {
        BasicAuthInterceptor(username, password)
    } else if(!credentialKey.isNullOrEmpty()){
        TokenAuthInterceptor(credentialKey)
    } else {
        null
    }
}

fun buildOkHttpClient(
    configuration: RemoteConfiguration,
    addInterceptor: Boolean = true,
    followRedirect: Boolean = false
): OkHttpClient.Builder {
    val builder = HttpClientBuilderFactory.create()
    builder.readTimeout(configuration.network.readTimeout, TimeUnit.MILLISECONDS)
    builder.connectTimeout(configuration.network.connectTimeout, TimeUnit.MILLISECONDS)
    builder.proxy(createProxy(configuration.network.proxy))
    builder.proxyAuthenticator(createProxyAuthenticator(configuration.network.proxy))
    if (addInterceptor) {
        createAuthenticateInterceptor(configuration.credentials)?.let { builder.addInterceptor(it) }
    }
    if (followRedirect) {
        builder.followRedirects(true)
        builder.followSslRedirects(true)
    }
    builder.retryOnConnectionFailure(true)
    return builder
}

/**
 * 从[remoteNodes]中找出路径为[fullPath]的节点，并获取其元数据列表
 *
 * @param remoteNodes 远程节点列表
 * @param fullPath 待查找的节点路径
 */
fun findRemoteMetadata(remoteNodes: List<Any>, fullPath: String): List<MetadataModel>? {
    val remoteNode = remoteNodes.firstOrNull {
        it is Map<*, *> && it[NodeDetail::fullPath.name] == fullPath
    } as Map<String, Any?>?
    return (remoteNode?.get(NodeDetail::nodeMetadata.name) as List<Map<String, Any?>>?)?.map {
        MetadataModel(
            key = it[MetadataModel::key.name] as String,
            value = it[MetadataModel::value.name] as String,
            system = it[MetadataModel::system.name] as Boolean? ?: false,
            description = it[MetadataModel::description.name]?.toString(),
            link = it[MetadataModel::link.name]?.toString(),
        )
    }
}

/**
 * 从[remoteNodes]中查询出[fullPath]的父节点，将其元数据更新到[fullPath]的本地父节点
 */
fun MetadataService.updateParentMetadata(
    remoteNodes: List<Any>,
    projectId: String,
    repoName: String,
    fullPath: String
) {
    val parents = PathUtils.resolveAncestorFolder(fullPath)
    for (parentFullPath in parents) {
        if (parentFullPath == ROOT) {
            continue
        }
        val parentMetadataList = findRemoteMetadata(remoteNodes, parentFullPath)
        if (!parentMetadataList.isNullOrEmpty()) {
            saveMetadata(
                MetadataSaveRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = parentFullPath,
                    nodeMetadata = parentMetadataList
                )
            )
        }
    }
}
