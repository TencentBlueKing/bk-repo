package com.tencent.bkrepo.common.security.util

import com.tencent.bkrepo.common.api.exception.AWS4AuthenticationException
import com.tencent.bkrepo.common.security.http.aws4.AWS4AuthCredentials
import org.springframework.util.StringUtils
import java.io.UnsupportedEncodingException
import java.lang.Exception
import java.lang.StringBuilder
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
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
        data: AWS4AuthCredentials
    ): Boolean {
        val httpMethod = data.method
        val heardMap: MutableMap<String, String> = HashMap<String, String>()
        heardMap["x-amz-content-sha256"] = data.contentHash
        heardMap["x-amz-date"] = data.requestDate
        heardMap["host"] = data.host
        val queryString = data.queryString
        var authorization = data.authorization
        var requestDate = data.requestDate
        //示例
        // AWS4-HMAC-SHA256 Credential=admin/20231109/us-east-1/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=275a5ae2d72c170dc8c464f59a487818c374b2a79bfbfaa080c358f77307d484

        ///region authorization拆分
        val parts = authorization.trim().split(",").toTypedArray()
        //第一部分-凭证范围
        val credential = parts[0].split("=").toTypedArray()[1]
        val credentials = credential.split("/").toTypedArray()
        val accessKey = credentials[0]
        if (data.accessKeyId != accessKey) {
            return false
        }
        val date = credentials[1]
        val region = credentials[2]
        val service = credentials[3]
        val aws4Request = credentials[4]
        //第二部分-签名头中包含哪些字段
        val signedHeader = parts[1].split("=").toTypedArray()[1]
        val signedHeaders = signedHeader.split(";").toTypedArray()
        //第三部分-生成的签名
        val signature = parts[2].split("=").toTypedArray()[1]

        ///待签名字符串
        var stringToSign: String? = ""
        //签名由4部分组成
        //1-Algorithm – 用于创建规范请求的哈希的算法。对于 SHA-256，算法是 AWS4-HMAC-SHA256。
        stringToSign += "AWS4-HMAC-SHA256\n"
        //2-RequestDateTime – 在凭证范围内使用的日期和时间。
        stringToSign += "$requestDate\n"
        //3-CredentialScope – 凭证范围。这会将生成的签名限制在指定的区域和服务范围内。该字符串采用以下格式：YYYYMMDD/region/service/aws4_request
        stringToSign += "$date/$region/$service/$aws4Request\n"
        //4-HashedCanonicalRequest – 规范请求的哈希。
        //<HTTPMethod>\n
        //<CanonicalURI>\n
        //<CanonicalQueryString>\n
        //<CanonicalHeaders>\n
        //<SignedHeaders>\n
        //<HashedPayload>
        var hashedCanonicalRequest = ""
        //4.1-HTTP Method
        hashedCanonicalRequest += "$httpMethod\n"
        //4.2-Canonical URI
        hashedCanonicalRequest += "${data.uri}\n"
        //4.3-Canonical Query String
        hashedCanonicalRequest += if (!StringUtils.isEmpty(queryString)) {
            val queryStringMap = parseQueryParams(queryString)
            val keyList: List<String> = ArrayList(queryStringMap.keys)
            Collections.sort(keyList)
            val queryStringBuilder = StringBuilder("")
            for (key in keyList) {
                queryStringBuilder.append(key).append("=").append(queryStringMap[key]).append("&")
            }
            queryStringBuilder.deleteCharAt(queryStringBuilder.lastIndexOf("&"))
            "${queryStringBuilder.toString()}\n"
        } else {
            "$queryString\n"
        }
        //4.4-Canonical Headers
        for (name in signedHeaders) {
            hashedCanonicalRequest += "$name:${heardMap[name]}\n"
        }
        hashedCanonicalRequest += "\n"
        //4.5-Signed Headers
        hashedCanonicalRequest += "$signedHeader\n"
        //4.6-Hashed Payload
        hashedCanonicalRequest += data.contentHash
        stringToSign += doHex(hashedCanonicalRequest)
        ///endregion

        ///重新生成签名
        //计算签名的key
        val kSecret = "AWS4${data.secretAccessKey}".toByteArray(charset("UTF8"))
        val kDate = doHmacSHA256(kSecret, date)
        val kRegion = doHmacSHA256(kDate, region)
        val kService = doHmacSHA256(kRegion, service)
        val signatureKey = doHmacSHA256(kService, aws4Request)
        //计算签名
        val authSignature = doHmacSHA256(signatureKey, stringToSign)

        //对签名编码处理
        val strHexSignature = doBytesToHex(authSignature)

        return signature == strHexSignature
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

    internal val hexArray = "0123456789ABCDEF".toCharArray()
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
        try {
            if (queryString != null && !queryString.isEmpty()) {
                val queryParamsArray = queryString.split("\\&".toRegex()).toTypedArray()
                for (param in queryParamsArray) {
                    val keyValue = param.split("\\=".toRegex()).toTypedArray()
                    if (keyValue.size == 1) {
                        val key = keyValue[0]
                        val value = ""
                        queryParams[key] = value
                    } else if (keyValue.size == 2) {
                        val key = keyValue[0]
                        val value = keyValue[1]
                        queryParams[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            throw AWS4AuthenticationException()
        }
        return queryParams
    }
}