package com.tencent.bkrepo.auth.config

import com.tencent.bk.sdk.iam.config.IamConfiguration
import com.tencent.bk.sdk.iam.helper.AuthHelper
import com.tencent.bk.sdk.iam.service.HttpClientService
import com.tencent.bk.sdk.iam.service.PolicyService
import com.tencent.bk.sdk.iam.service.TokenService
import com.tencent.bk.sdk.iam.service.impl.DefaultHttpClientServiceImpl
import com.tencent.bk.sdk.iam.service.impl.GrantServiceImpl
import com.tencent.bk.sdk.iam.service.impl.PolicyServiceImpl
import com.tencent.bk.sdk.iam.service.impl.TokenServiceImpl
import com.tencent.bkrepo.auth.service.bkiam.IamEsbClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
class BkiamConfiguration {

    @Value("\${auth.iam.systemId:}")
    val iamSystemId = ""

    @Value("\${auth.iam.baseUrl:}")
    val iamBaseUrl = ""

    @Value("\${auth.iam.appCode:}")
    val appCode = ""

    @Value("\${auth.iam.appSecret:}")
    val appSecret = ""

    @Bean
    fun iamConfiguration() = IamConfiguration(iamSystemId, appCode, appSecret, iamBaseUrl)

    @Bean
    fun iamHttpClient(iamConfiguration: IamConfiguration) = DefaultHttpClientServiceImpl(iamConfiguration)

    @Bean
    fun iamPolicyService(
        @Autowired iamConfiguration: IamConfiguration,
        @Autowired httpClientService: HttpClientService
    ) = PolicyServiceImpl(iamConfiguration, httpClientService)

    @Bean
    fun tokenService(
        @Autowired iamConfiguration: IamConfiguration,
        @Autowired httpClientService: HttpClientService
    ) = TokenServiceImpl(iamConfiguration, httpClientService)

    @Bean
    fun authHelper(
        @Autowired tokenService: TokenService,
        @Autowired policyService: PolicyService,
        @Autowired iamConfiguration: IamConfiguration
    ) = AuthHelper(tokenService, policyService, iamConfiguration)

    @Bean
    fun grantService(
        @Autowired defaultHttpClientServiceImpl: DefaultHttpClientServiceImpl,
        @Autowired iamConfiguration: IamConfiguration
    ) = GrantServiceImpl(defaultHttpClientServiceImpl, iamConfiguration)

    @Bean
    @ConditionalOnMissingBean
    fun iamEsbService() = IamEsbClient()
}