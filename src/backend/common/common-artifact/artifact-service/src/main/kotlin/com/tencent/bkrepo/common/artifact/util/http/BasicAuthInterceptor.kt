package com.tencent.bkrepo.common.artifact.util.http

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import org.springframework.http.HttpHeaders

class BasicAuthInterceptor(user: String, password: String) : Interceptor {

    private val credentials = Credentials.basic(user, password)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authenticatedRequest = request.newBuilder()
            .header(HttpHeaders.AUTHORIZATION, credentials).build()
        return chain.proceed(authenticatedRequest)
    }
}
