package com.tencent.bkrepo.common.api.util.okhttp

import com.tencent.bkrepo.common.api.util.AsyncUtils
import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import io.micrometer.observation.ObservationRegistry
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale.getDefault

class TraceInterceptor(
    private val registry: ObservationRegistry,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val spanName = "http ${request.method}".lowercase(getDefault())
        val methodKV = KeyValue.of("http.method", request.method)
        val urlKV = KeyValue.of("http.url", request.url.toString())
        val lowCardinalityKeyValues = KeyValues.of(methodKV)
        val highCardinalityKeyValues = KeyValues.of(urlKV)
        return AsyncUtils.newSpan(registry, spanName, lowCardinalityKeyValues, highCardinalityKeyValues) {
            try {
                val response = chain.proceed(request)
                registry.currentObservation?.lowCardinalityKeyValue("http.status_code", response.code.toString())
                response
            } catch (e: Exception) {
                registry.currentObservation?.highCardinalityKeyValue("http.error", e.message.toString())
                throw e
            }
        }
    }
}