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

package com.tencent.bkrepo.s3.artifact.utils

import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.s3.artifact.auth.AWS4AuthCredentials
import com.tencent.bkrepo.s3.exception.AWS4AuthenticationException
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS4签名验证
 */
object AWS4AuthUtil {
    /**
     * 签名正确与否
     */
    fun validAuthorization(
        authCredentials: AWS4AuthCredentials,
        secretAccessKey: String
    ): Boolean {
        // 解析签名信息
        val authInfo = parseAuthorization(authCredentials.authorization)
        if (authCredentials.accessKeyId != authInfo.accessKey) {
            return false
        }
        // 待签名字符串
        val stringToSign: String = buildStringToSign(
            authCredentials,
            authInfo
        )
        // 计算签名的key
        val signatureKey = calculateSignatureKey(secretAccessKey, authInfo)
        // 重新生成签名
        val strHexSignature = calculateHexSignature(stringToSign, signatureKey)
        return authInfo.signature == strHexSignature
    }

    data class AuthorizationInfo(
        val accessKey: String,
        val date: String,
        val region: String,
        val service: String,
        val aws4Request: String,
        val signature: String,
        val signedHeader: String,
        val signedHeaders: Array<String>
    )

    private fun parseAuthorization(authorization: String): AuthorizationInfo {
        /**
         * authorization示例
         * AWS4-HMAC-SHA256 Credential=admin/20231109/us-east-1/s3/aws4_request, SignedHeaders=host;
         * x-amz-content-sha256;x-amz-date,
         * Signature=275a5ae2d72c170dc8c464f59a487818c374b2a79bfbfaa080c358f77307d484
         */
        //region authorization拆分
        val parts = authorization.trim().split(",").toTypedArray()
        //第一部分-凭证范围
        val credential = parts[0].split("=").toTypedArray()[1]
        val credentials = credential.split("/").toTypedArray()
        //第二部分-签名头中包含哪些字段
        val signedHeader = parts[1].split("=").toTypedArray()[1]

        return AuthorizationInfo(
            accessKey = credentials[0],
            date = credentials[1],
            region = credentials[2],
            service = credentials[3],
            aws4Request = credentials[4],
            signature = parts[2].split("=").toTypedArray()[1],
            signedHeader = signedHeader,
            signedHeaders = signedHeader.split(";").toTypedArray()
        )
    }

    private fun buildStringToSign(
        authCredentials: AWS4AuthCredentials,
        authInfo: AuthorizationInfo
    ): String {
        ///待签名字符串
        var stringToSign = ""
        //签名由4部分组成
        //1-Algorithm – 用于创建规范请求的哈希的算法。对于 SHA-256，算法是 AWS4-HMAC-SHA256。
        stringToSign += "AWS4-HMAC-SHA256\n"
        //2-RequestDateTime – 在凭证范围内使用的日期和时间。
        stringToSign += "${authCredentials.requestDate}\n"
        //3-CredentialScope – 凭证范围。
        //这会将生成的签名限制在指定的区域和服务范围内。该字符串采用以下格式：YYYYMMDD/region/service/aws4_request
        stringToSign += "${authInfo.date}/${authInfo.region}/${authInfo.service}/${authInfo.aws4Request}\n"
        //4-HashedCanonicalRequest – 规范请求的哈希。
        //<HTTPMethod>\n
        //<CanonicalURI>\n
        //<CanonicalQueryString>\n
        //<CanonicalHeaders>\n
        //<SignedHeaders>\n
        //<HashedPayload>
        var hashedCanonicalRequest = ""
        //4.1-HTTP Method
        hashedCanonicalRequest += "${authCredentials.method}\n"
        //4.2-Canonical URI
        hashedCanonicalRequest += "${authCredentials.uri}\n"
        //4.3-Canonical Query String
        hashedCanonicalRequest += if (authCredentials.queryString.isNotEmpty()) {
            val queryStringMap = parseQueryParams(authCredentials.queryString)
            val keyList = queryStringMap.keys.sorted()
            val queryStringBuilder = StringBuilder("")
            for (key in keyList) {
                queryStringBuilder.append(key).append("=").append(queryStringMap[key]).append("&")
            }
            queryStringBuilder.deleteCharAt(queryStringBuilder.lastIndexOf("&"))
            "$queryStringBuilder\n"
        } else {
            "${authCredentials.queryString}\n"
        }
        //4.4-Canonical Headers
        for (name in authInfo.signedHeaders) {
            hashedCanonicalRequest += "$name:${HeaderUtils.getHeader(name)}\n"
        }
        hashedCanonicalRequest += "\n"
        //4.5-Signed Headers
        hashedCanonicalRequest += "${authInfo.signedHeader}\n"
        //4.6-Hashed Payload
        hashedCanonicalRequest += authCredentials.contentHash

        stringToSign += doHex(hashedCanonicalRequest)

        return stringToSign
    }

    private fun calculateSignatureKey(
        secretAccessKey: String,
        authInfo: AuthorizationInfo
    ): ByteArray {
        val kSecret = "AWS4$secretAccessKey".toByteArray(charset("UTF8"))
        val kDate = doHmacSHA256(kSecret, authInfo.date)
        val kRegion = doHmacSHA256(kDate, authInfo.region)
        val kService = doHmacSHA256(kRegion, authInfo.service)
        return doHmacSHA256(kService, authInfo.aws4Request)
    }

    private fun calculateHexSignature(stringToSign: String, signatureKey: ByteArray): String {
        val authSignature = doHmacSHA256(signatureKey, stringToSign)
        return doBytesToHex(authSignature)
    }

    /**
     * 获取authorization中的accessKey
     */
    fun getAccessKey(authorization: String): String {
        ///authorization拆分
        val parts = authorization.trim().split(",").toTypedArray()
        //第一部分-凭证范围
        val credential = parts[0].split("=").toTypedArray()[1]
        val credentials = credential.split("/").toTypedArray()
        return credentials[0]
    }

    @Throws(AWS4AuthenticationException::class)
    private fun doHmacSHA256(key: ByteArray, data: String?): ByteArray {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key, algorithm))
        return mac.doFinal(data!!.toByteArray(charset("UTF8")))
    }

    private val hexArray = "0123456789ABCDEF".toCharArray()
    private fun doBytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars).toLowerCase()
    }

    private fun doHex(data: String): String? {
        val messageDigest: MessageDigest
        try {
            messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(data.toByteArray(charset("UTF-8")))
            val digest = messageDigest.digest()
            return String.format("%064x", BigInteger(1, digest))
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseQueryParams(queryString: String?): Map<String, String> {
        val queryParams: MutableMap<String, String> = HashMap()
        if (!queryString.isNullOrEmpty()) {
            val queryParamsArray = queryString.split("&")
            for (param in queryParamsArray) {
                val keyValue = param.split("=")
                val key = keyValue[0]
                val value = if (keyValue.size == 2) keyValue[1] else ""
                queryParams[key] = value
            }
        }
        return queryParams
    }
}
