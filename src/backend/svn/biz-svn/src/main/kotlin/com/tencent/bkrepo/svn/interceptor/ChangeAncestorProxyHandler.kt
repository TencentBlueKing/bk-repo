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

package com.tencent.bkrepo.svn.interceptor

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.ensurePrefix
import com.tencent.bkrepo.common.service.util.proxy.DefaultProxyCallHandler
import com.tencent.bkrepo.common.service.util.proxy.HttpProxyUtil.headers
import com.tencent.bkrepo.svn.config.SvnProperties
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 制品库的SVN仓库路径前缀为/{projectId}/{repoName}，与代理的仓库可能不一致，需要调整请求与响应中的前缀
 */
class ChangeAncestorProxyHandler(private val svnProperties: SvnProperties) : DefaultProxyCallHandler() {
    override fun pre(
        proxyRequest: HttpServletRequest,
        proxyResponse: HttpServletResponse,
        request: Request
    ): Request {
        val oldPrefix = if (svnProperties.repoPrefix.isEmpty()){
            prefix(proxyRequest.servletPath)
        } else {
            svnProperties.repoPrefix + prefix(proxyRequest.servletPath)
        }
        val newPrefix = prefix(request.url.encodedPath)
        if (oldPrefix.isNullOrEmpty() || newPrefix.isNullOrEmpty()) {
            logger.warn("prefix not found: oldPrefix[$oldPrefix], newPrefix[$newPrefix]")
            return request
        }

        val builder = request.newBuilder()
        proxyRequest.headers().forEach { (k, v) ->
            if (k in requestHeaders) {
                val newHeaderValue = newPrefix + v.substringAfter(oldPrefix)
                builder.header(k, newHeaderValue)
            }
        }

        if (proxyRequest.contentType?.startsWith("text/xml") == true) {
            val oldBody = proxyRequest.inputStream.readBytes().toString(Charsets.UTF_8)
            val newBody = oldBody
                .replace(ELEMENT_PATH_PREFIX + oldPrefix, ELEMENT_PATH_PREFIX + newPrefix)
                .replace(ELEMENT_HREF_PREFIX + oldPrefix, ELEMENT_HREF_PREFIX + newPrefix)
            builder.method(proxyRequest.method, RequestBody.create("text/xml".toMediaType(), newBody))
        }

        return builder.build()
    }

    override fun after(proxyRequest: HttpServletRequest, proxyResponse: HttpServletResponse, response: Response) {
        if (!response.isSuccessful) {
            return super.after(proxyRequest, proxyResponse, response)
        }

        val oldPrefix = prefix(response.request.url.encodedPath)
        val newPrefix = if (svnProperties.repoPrefix.isEmpty()){
            prefix(proxyRequest.servletPath)
        } else {
            svnProperties.repoPrefix + prefix(proxyRequest.servletPath)
        }
        if (oldPrefix.isNullOrEmpty() || newPrefix.isNullOrEmpty()) {
            return super.after(proxyRequest, proxyResponse, response)
        }

        // 转发状态码
        proxyResponse.status = response.code

        // 转发头
        response.headers.forEach { (key, value) ->
            if (key in responseHeaders) {
                val newHeaderValue = newPrefix + value.substringAfter(oldPrefix)
                proxyResponse.setHeader(key, newHeaderValue)
            } else {
                proxyResponse.addHeader(key, value)
            }
        }

        // 转发body
        if (response.header(HttpHeaders.CONTENT_TYPE)?.startsWith("text/xml") == true) {
            // 替换XML中的href标签中的路径前缀
            val oldBody = response.body!!.string()
            val newBody =
                oldBody.replace(ELEMENT_HREF_PREFIX + oldPrefix, ELEMENT_HREF_PREFIX + newPrefix)
            proxyResponse.writer.write(newBody)
            proxyResponse.setHeader(HttpHeaders.CONTENT_LENGTH, newBody.length.toString())
        } else {
            response.body?.byteStream()?.use {
                it.copyTo(proxyResponse.outputStream)
            }
        }
    }

    /**
     * 获取/{projectId}/{repoName}前缀
     *
     * /a/b/c -> /a/b
     * /a/b -> /a/b
     * /a -> null
     * a/b/c -> /a/b
     */
    private fun prefix(path: String): String? {
        var thirdSlashIndex = -1
        var slashCount = if (path[0] != '/') {
            1
        } else {
            0
        }

        for ((index, c) in path.withIndex()) {
            if (c == '/') {
                slashCount++
            }
            if (slashCount == 3) {
                thirdSlashIndex = index
                break
            }
        }

        val prefix = when (slashCount) {
            2 -> path
            3 -> path.substring(0, thirdSlashIndex)
            else -> null
        }

        return prefix?.ensurePrefix("/")?.removeSuffix("/")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ChangeAncestorProxyHandler::class.java)
        private const val HEADER_SVN_DELTA_BASE = "X-SVN-VR-Base"

        private const val HEADER_SVN_ROOT_URI = "SVN-Repository-Root"
        private const val HEADER_SVN_ME_RESOURCE = "SVN-Me-Resource"
        private const val HEADER_SVN_REV_STUB = "SVN-Rev-Stub"
        private const val HEADER_SVN_REV_ROOT_STUB = "SVN-Rev-Root-Stub"
        private const val HEADER_SVN_TXN_STUB = "SVN-Txn-Stub"
        private const val HEADER_SVN_TXN_ROOT_STUB = "SVN-Txn-Root-Stub"
        private const val HEADER_SVN_VTXN_STUB = "SVN-VTxn-Stub"
        private const val HEADER_SVN_VTXN_ROOT_STUB = "SVN-VTxn-Root-Stub"

        val requestHeaders = arrayOf(HEADER_SVN_DELTA_BASE)
        val responseHeaders = arrayOf(
            HEADER_SVN_DELTA_BASE,
            HEADER_SVN_ROOT_URI,
            HEADER_SVN_ME_RESOURCE,
            HEADER_SVN_REV_STUB,
            HEADER_SVN_REV_ROOT_STUB,
            HEADER_SVN_TXN_STUB,
            HEADER_SVN_TXN_ROOT_STUB,
            HEADER_SVN_VTXN_STUB,
            HEADER_SVN_VTXN_ROOT_STUB,
        )

        // xml element
        private const val ELEMENT_HREF_PREFIX = "href>"
        private const val ELEMENT_PATH_PREFIX = "path>"
    }
}
