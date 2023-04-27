package com.tencent.bkrepo.replication.replica.base.interceptor

import com.google.common.hash.Hashing
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.util.HttpSigner
import com.tencent.bkrepo.common.service.util.HttpSigner.ACCESS_KEY
import com.tencent.bkrepo.common.service.util.HttpSigner.APP_ID
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN_TIME
import com.tencent.bkrepo.common.service.util.HttpSigner.TIME_SPLIT
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Response
import okio.Buffer
import org.apache.commons.codec.digest.HmacAlgorithms

/**
 * 签名拦截器
 * */
class SignInterceptor(private val clusterInfo: ClusterInfo) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        with(clusterInfo) {
            val request = chain.request()
            val startTime = System.currentTimeMillis() / HttpSigner.MILLIS_PER_SECOND
            var endTime = startTime + HttpSigner.REQUEST_TTL
            val urlBuilder = request.url.newBuilder()
            val body = request.body
            /*
            * 文件请求使用multipart/form-data，为避免读取文件，这里使用空串。表单参数应该包含文件的sha256。
            * 通过对表单参数的签名，来实现对文件请求的签名。
            * */
            val bodyToHash = if (body != null && body !is MultipartBody) {
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readByteArray()
            } else {
                // 文件请求TTL不限制
                endTime = startTime + Int.MAX_VALUE
                StringPool.EMPTY.toByteArray()
            }
            // 添加签名必要参数
            urlBuilder.addQueryParameter(APP_ID, appId)
                .addQueryParameter(ACCESS_KEY, accessKey)
                .addQueryParameter(SIGN_TIME, "$startTime$TIME_SPLIT$endTime")
            val newRequest = request.newBuilder().url(urlBuilder.build()).build()
            val bodyHash = Hashing.sha256().hashBytes(bodyToHash).toString()
            val realPath = request.url.toUri().path
            val signPath = if (realPath.startsWith("/$SERVICE_NAME")) {
                realPath.removePrefix("/$SERVICE_NAME")
            } else {
                realPath
            }
            val sig = HttpSigner.sign(newRequest, signPath, bodyHash, secretKey!!, HmacAlgorithms.HMAC_SHA_1.getName())
            urlBuilder.addQueryParameter(SIGN, sig)
            val newRequest2 = request.newBuilder().url(urlBuilder.build()).build()
            return chain.proceed(newRequest2)
        }
    }

    companion object {
        private const val SERVICE_NAME = "replication"
    }
}
