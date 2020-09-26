package com.tencent.bkrepo.auth.config

import com.tencent.bk.sdk.iam.config.IamConfiguration
import com.tencent.bk.sdk.iam.service.impl.DefaultHttpClientServiceImpl
import com.tencent.bk.sdk.iam.service.impl.TokenServiceImpl
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
class BkiamConfiguration {

    @Value("\${auth.iam.baseUrl:}")
    val iamBaseUrl = ""

    @Value("\${auth.iam.appCode:}")
    val appCode = ""

    @Value("\${auth.iam.appSecret:}")
    val appSecret = ""

    @Bean
    fun iamConfiguration() = IamConfiguration(appCode, appCode, appSecret, iamBaseUrl)

    @Bean
    fun httpClient(iamConfiguration: IamConfiguration) = DefaultHttpClientServiceImpl(iamConfiguration)

    @Bean
    fun tokenService(iamConfiguration: IamConfiguration) = TokenServiceImpl(iamConfiguration, httpClient(iamConfiguration))
}