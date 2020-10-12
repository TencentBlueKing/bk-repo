package com.tencent.bkrepo.dockerapi.util

import com.tencent.bkrepo.dockerapi.client.TrustAllHostnameVerifier
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object HttpUtils {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5L, TimeUnit.SECONDS)
        .readTimeout(30L, TimeUnit.SECONDS)
        .writeTimeout(30L, TimeUnit.SECONDS)
        .hostnameVerifier(TrustAllHostnameVerifier())
        .build()

    fun getQueryStr(paramsMap: Map<String, String>): String {
        return if (paramsMap.isEmpty()) {
            ""
        } else {
            val paramList = mutableListOf<String>()
            for ((k, v) in paramsMap) {
                paramList.add("$k=${URLEncoder.encode(v, "utf8")}")
            }
            paramList.joinToString("&")
        }
    }

    fun doRequest(okHttpClient: OkHttpClient, request: Request, retry: Int = 0, acceptCode: Set<Int> = setOf()): ApiResponse {
        try {
            val response = okHttpClient.newBuilder().build().newCall(request).execute()
            val responseCode = response.code()
            val responseContent = response.body()!!.string()
            if (response.isSuccessful || acceptCode.contains(responseCode)) {
                return ApiResponse(responseCode, responseContent)
            }
            logger.warn("http request failed, code: $responseCode, responseContent: $responseContent")
            throw RuntimeException("http request failed, code: $responseCode")
        } catch (e: Exception) {
            if (retry > 0) {
                logger.warn("http request error, cause: ${e.message}")
                Thread.sleep(1000)
                return doRequest(request, retry - 1, acceptCode)
            } else {
                throw e
            }
        }
    }

    fun doRequest(request: Request, retry: Int = 0, acceptCode: Set<Int> = setOf()): ApiResponse {
        return doRequest(okHttpClient, request, retry, acceptCode)
    }

    private val logger = LoggerFactory.getLogger(HttpUtils::class.java)
}