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

package com.tencent.bkrepo.common.storage.innercos.request

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.PATH_DELIMITER
import com.tencent.bkrepo.common.storage.innercos.client.ClientConfig
import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.AUTHORIZATION
import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.HOST
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.sign.CosSigner
import com.tencent.bkrepo.common.storage.innercos.urlEncode
import okhttp3.RequestBody
import java.util.TreeMap

abstract class CosRequest(
    val method: HttpMethod,
    uri: String
) {
    val headers = TreeMap<String, String>()
    val parameters = TreeMap<String, String?>()
    var url: String = StringPool.EMPTY

    /**
     * 参数url encode编码时，时候将编码后的内容转为小写，如%2F -> %2f
     */
    private var encodeToLower: Boolean = true

    /**
     * 请求uri，如/test
     */
    private val requestUri: String = StringBuilder().append(PATH_DELIMITER).append(uri.trim(PATH_DELIMITER)).toString()

    /**
     * path-style 访问时的路径前缀，如 /bucket
     */
    private var pathPrefix: String = StringPool.EMPTY

    /**
     * 构造请求体
     */
    abstract fun buildRequestBody(): RequestBody?

    open fun sign(credentials: InnerCosCredentials, config: ClientConfig): String {
        headers[AUTHORIZATION]?.let { return it }
        encodeToLower = !credentials.public
        val endpoint = config.endpointBuilder.buildEndpoint(credentials.region, credentials.bucket)
        val resolvedHost = config.endpointResolver.resolveEndpoint(endpoint)
        if (config.pathStyle) {
            // path-style：客户端访问 IP，Host/URL 必须一致；默认 80 端口不写入 Host
            pathPrefix = "$PATH_DELIMITER${credentials.bucket}"
            val host = resolvedHost.removeSuffix(HTTP_DEFAULT_PORT_SUFFIX)
            headers[HOST] = host
            url = buildUrl(config.httpProtocol.getScheme(), host, getFormatUri())
        } else {
            // virtual-hosted：Host 用域名参与签名，URL 可用解析后的地址
            headers[HOST] = endpoint
            url = buildUrl(config.httpProtocol.getScheme(), resolvedHost, getFormatUri())
        }
        return CosSigner.sign(this, credentials, config.signExpired).also { headers[AUTHORIZATION] = it }
    }

    private fun buildUrl(scheme: String, host: String, uri: String): String {
        val base = scheme + host + uri
        return if (parameters.isEmpty()) base else "$base?${getFormatParameters()}"
    }

    /**
     * 返回请求方法，小写格式，如get
     */
    fun getFormatMethod(): String = method.name.toLowerCase()

    /**
     * 返工格式化后的请求uri，如/test；path-style 时为 /bucket/test
     */
    fun getFormatUri(): String = pathPrefix + requestUri

    /**
     * 返回格式化之后的参数key列表
     * 格式: key1;key2;key3 字典升序排序
     * 规则: URLEncode编码，并转换为小写形式
     * 差异: inner: URLEncode编码为小写%2f  public: URLEncode编码为大写%2F
     */
    fun getFormatParameterKeys(): String {
        return parameters.keys.joinToString(";") { it.toLowerCase().urlEncode(encodeToLower) }
    }

    /**
     * 返回格式化之后的参数列表
     * 格式: key1=value1&key2=value2&key3=value3 字典升序排序
     * 规则: key: URLEncode编码，并转换为小写形式  value: URLEncode编码
     * 差异: inner: URLEncode编码为小写%2f  public: URLEncode编码为大写%2F
     */
    fun getFormatParameters(): String {
        return parameters.map {
            "${it.key.urlEncode(encodeToLower)}=${it.value?.urlEncode(encodeToLower).orEmpty()}"
        }.joinToString("&")
    }

    /**
     * 返回格式化之后的header key列表
     * 格式: key1;key2;key3 字典升序排序
     * 规则: URLEncode编码，并转换为小写形式
     * 差异: inner: URLEncode编码为小写%2f  public: URLEncode编码为大写%2F
     */
    fun getFormatHeaderKeys(): String {
        return headers.keys.joinToString(";") { it.toLowerCase().urlEncode(encodeToLower) }
    }

    /**
     * 返回格式化之后的header列表
     * 格式: key1=value1&key2=value2&key3=value3 字典升序排序
     * 规则: key: URLEncode编码，并转换为小写形式  value: URLEncode编码
     * 差异: inner: URLEncode编码为小写%2f  public: URLEncode编码为大写%2F
     */
    fun getFormatHeaders(): String {
        return headers.map {
            "${it.key.toLowerCase().urlEncode(encodeToLower)}=${it.value.urlEncode(encodeToLower)}"
        }.joinToString("&")
    }

    companion object {
        @JvmStatic
        protected val xmlMapper = XmlMapper()

        private const val HTTP_DEFAULT_PORT_SUFFIX = ":80"
    }
}
