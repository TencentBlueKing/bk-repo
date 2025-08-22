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

package com.tencent.bkrepo.common.service.util

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.urlEncode
import feign.RequestTemplate
import jakarta.servlet.http.HttpServletRequest
import okhttp3.MultipartBody
import okhttp3.Request
import okio.Buffer
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import org.apache.commons.fileupload.ParameterParser

/**
 * http请求签名工具，提供了servlet/feign/okhttp请求统一签名
 *
 * 待签名字符串=($key$uri$method$sortedParameters$bodyHash).toLowerCase()
 * 查询和表单参数使用升序排列。
 * 目前没有需要签名的header,所以header未进行签名。
 * body使用sha256签名
 * 因为文件上传使用的是multipart/form-data,未避免对文件请求直接进行签名，所以使用空串代替。
 * 文件请求的签名是通过把文件的sha256放置到表单参数，然后对表单参数进行签名，服务端会校验文件sha256。
 * */
object HttpSigner {
    private val supportedAlgorithms = HmacAlgorithms.values().map { it.getName() }.toSet()

    /**
     * 对http servlet request进行签名
     * @param request http请求
     * @param key 签名的密钥
     * @param algorithm 签名使用的算法
     * */
    fun sign(request: HttpServletRequest, uri: String, bodyHash: String, key: String, algorithm: String): String {
        verifyAlgorithm(algorithm)
        val valueToDigest = getSignatureStr(request, uri, bodyHash)
        return HmacUtils(algorithm, key.toByteArray())
            .hmacHex(valueToDigest)
    }

    /**
     * feign请求签名
     * @param requestTemplate 请求模板
     * @param bodyHash 请求体的hash
     * @param key 签名的密钥
     * @param algorithm 签名使用的算法
     * */
    fun sign(requestTemplate: RequestTemplate, bodyHash: String, key: String, algorithm: String): String {
        verifyAlgorithm(algorithm)
        val uri = requestTemplate.path()
        val method = requestTemplate.method() ?: throw IllegalArgumentException("Missing method parameter.")
        // queries 已经encode了
        val sortedParameters = sortParametersWithCollection(requestTemplate.queries())
        return sign0(uri, method, sortedParameters, bodyHash, key, algorithm)
    }

    /**
     * okhttp签名
     * @param request 请求模板
     * @param uri 自定义签名的uri
     * @param bodyHash 请求体的hash
     * @param key 签名的密钥
     * @param algorithm 签名使用的算法
     * */
    fun sign(request: Request, uri: String, bodyHash: String, key: String, algorithm: String): String {
        val httpUrl = request.url
        val method = request.method
        val parser = ParameterParser()
        val queries = parser.parse(httpUrl.query, '&')
        val body = request.body
        // 将表单参数加入签名
        if (body is MultipartBody) {
            addFormParameters(body, parser, queries)
        }
        val sortedParameters = sortParametersWithMap(queries)
        return sign0(uri, method, sortedParameters, bodyHash, key, algorithm)
    }

    fun getSignatureStr(request: HttpServletRequest, uri: String, bodyHash: String): String {
        val method = request.method
        val needSignParameters = request.parameterMap.filter { it.key != SIGN }.toMap()
        val sortedParameters = sortParameters(needSignParameters)
        return "$uri$method$sortedParameters$bodyHash".toLowerCase()
    }

    private fun addFormParameters(
        body: MultipartBody,
        parser: ParameterParser,
        queries: MutableMap<String, String>
    ) {
        body.parts.forEach { part ->
            part.headers?.let {
                // form-data; name=""; filename=""
                val params = parser.parse(it.get(HttpHeaders.CONTENT_DISPOSITION), ';')
                val fileName = params["filename"]
                if (fileName == null) {
                    // k-v参数
                    val buffer = Buffer()
                    part.body.writeTo(buffer)
                    queries[params["name"]!!] = buffer.readByteArray().decodeToString()
                }
            }
        }
    }

    /**
     * 校验签名算法
     * */
    private fun verifyAlgorithm(algorithm: String) {
        if (!supportedAlgorithms.contains(algorithm)) {
            throw IllegalArgumentException("Not support $algorithm algorithm.")
        }
    }

    /**
     * 使用指定算法进行签名
     * */
    private fun sign0(
        uri: String,
        method: String,
        sortedParameters: String,
        bodyHash: String,
        key: String,
        algorithm: String
    ): String {
        val valueToDigest = "$uri$method$sortedParameters$bodyHash".toLowerCase()
        return HmacUtils(algorithm, key.toByteArray())
            .hmacHex(valueToDigest)
    }

    private fun sortParameters(parameterMap: Map<String, Array<String>>, encode: Boolean = true): String {
        val stringBuilder = StringBuilder()
        parameterMap.entries.sortedBy { it.key }.forEach {
            val s1 = "${it.key}${it.value.joinToString("")}"
            val s2 = if (encode) {
                s1.urlEncode()
            } else s1.replace("/", "%2f")
            stringBuilder.append(s2)
        }
        return stringBuilder.toString()
    }

    private fun sortParametersWithCollection(queries: Map<String, Collection<String>>): String {
        val parameterMap = mutableMapOf<String, Array<String>>()
        queries.forEach { (k, v) -> parameterMap[k] = v.toTypedArray() }
        return sortParameters(parameterMap, false)
    }

    private fun sortParametersWithMap(queries: Map<String, String>): String {
        val stringBuilder = StringBuilder()
        queries.entries.sortedBy { it.key }.forEach {
            stringBuilder.append("${it.key}${it.value}".urlEncode())
        }
        return stringBuilder.toString()
    }

    const val SIGN_TIME = "sign_time"
    const val SIGN_ALGORITHM = "sign_algorithm"
    const val REQUEST_TTL = 15 * 60 // 15min
    const val MILLIS_PER_SECOND = 1000
    const val ACCESS_KEY = "access_key"
    const val APP_ID = "app_id"
    const val SIGN = "sig"
    const val SIGN_BODY = "sign_body"
    const val TIME_SPLIT = ";"
    const val PROXY_NAME = "proxy_name"
    const val PROJECT_ID = "project_id"
}
