package com.tencent.bkrepo.common.artifact.repository.http

import okhttp3.OkHttpClient

/**
 *
 * @author: carrypan
 * @date: 2019/12/3
 */
object HttpClientBuilderFactory {

    private val okHttpClient by lazy { OkHttpClient.Builder().build() }

    fun create(): OkHttpClient.Builder = okHttpClient.newBuilder()
}
