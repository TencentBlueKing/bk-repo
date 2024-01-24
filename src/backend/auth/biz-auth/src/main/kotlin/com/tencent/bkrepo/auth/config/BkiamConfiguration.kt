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

package com.tencent.bkrepo.auth.config

import com.tencent.bk.sdk.iam.config.IamConfiguration
import com.tencent.bk.sdk.iam.helper.AuthHelper
import com.tencent.bk.sdk.iam.service.PolicyService
import com.tencent.bk.sdk.iam.service.TokenService
import com.tencent.bk.sdk.iam.service.impl.ApigwHttpClientServiceImpl
import com.tencent.bk.sdk.iam.service.impl.DefaultHttpClientServiceImpl
import com.tencent.bk.sdk.iam.service.impl.GrantServiceImpl
import com.tencent.bk.sdk.iam.service.impl.ManagerServiceImpl
import com.tencent.bk.sdk.iam.service.impl.TokenServiceImpl
import com.tencent.bk.sdk.iam.service.v2.impl.V2ManagerServiceImpl
import com.tencent.bk.sdk.iam.service.v2.impl.V2PolicyServiceImpl
import com.tencent.bkrepo.auth.condition.MultipleAuthCondition
import com.tencent.bkrepo.auth.service.bkiamv3.IamEsbClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration

@Configuration
@Conditional(MultipleAuthCondition::class)
class BkiamConfiguration {

    @Value("\${auth.iam.systemId:}")
    private val iamSystemId = ""

    @Value("\${auth.iam.baseUrl:}")
    private val iamBaseUrl = ""

    @Value("\${auth.iam.appCode:}")
    private val appCode = ""

    @Value("\${auth.iam.appSecret:}")
    private val appSecret = ""

    @Value("\${auth.iam.apigwBaseUrl:}")
    private val apigwBaseUrl = ""

    @Bean
    fun iamConfiguration() = IamConfiguration(iamSystemId, appCode, appSecret, iamBaseUrl, apigwBaseUrl)

    @Bean
    fun iamHttpClient(iamConfiguration: IamConfiguration) = DefaultHttpClientServiceImpl(iamConfiguration)

    @Bean
    fun iamPolicyService(
        @Autowired iamConfiguration: IamConfiguration
    ) = V2PolicyServiceImpl(apigwHttpClientService(iamConfiguration), iamConfiguration)

    @Bean
    fun tokenService(
        @Autowired iamConfiguration: IamConfiguration
    ) = TokenServiceImpl(iamConfiguration, apigwHttpClientService(iamConfiguration))

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

    // 接入V3(RBAC)
    /**
     * 鉴权类http实例。 管理类与鉴权类http实例分开，防止相互影响
     */
    @Bean
    fun apigwHttpClientService(iamConfiguration: IamConfiguration) = ApigwHttpClientServiceImpl(iamConfiguration)

    /**
     * 管理类http实例。 管理类与鉴权类http实例分开，防止相互影响
     */
    @Bean
    fun managerHttpClientService(iamConfiguration: IamConfiguration) = ApigwHttpClientServiceImpl(iamConfiguration)

    @Bean
    fun iamManagerServiceV2(
        iamConfiguration: IamConfiguration
    ) = V2ManagerServiceImpl(managerHttpClientService(iamConfiguration), iamConfiguration)

    @Bean
    fun iamManagerServiceV1(
        iamConfiguration: IamConfiguration
    ) = ManagerServiceImpl(managerHttpClientService(iamConfiguration), iamConfiguration)
}
