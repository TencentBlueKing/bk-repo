/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.analyst

import com.tencent.bkrepo.analyst.api.ScanClient
import com.tencent.bkrepo.analyst.api.ScanQualityClient
import com.tencent.bkrepo.analyst.config.AnalystProperties
import com.tencent.bkrepo.analyst.metadata.AnalystMetadataCustomizer
import com.tencent.bkrepo.analyst.sign.SignedNodeForwardServiceImpl
import com.tencent.bkrepo.common.artifact.manager.NodeForwardService
import com.tencent.bkrepo.common.artifact.sign.SignProperties
import com.tencent.bkrepo.common.metadata.listener.MetadataCustomizer
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.sign.SignConfigService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    AnalystProperties::class,
    SignProperties::class
)
class AnalystMetadataAutoConfiguration {
    @Bean
    @ConditionalOnProperty("analyst.enableForbidNotScanned", havingValue = "true")
    fun analystMetadataCustomizer(
        properties: AnalystProperties,
        scanQualityClient: ScanQualityClient
    ): MetadataCustomizer =
        AnalystMetadataCustomizer(properties, scanQualityClient)

    @Bean
    fun signNodeForwardService(
        signProperties: SignProperties,
        nodeService: NodeService,
        metadataService: MetadataService,
        scanClient: ScanClient,
        repositoryService: RepositoryService,
        signConfigService: SignConfigService
    ): NodeForwardService =
        SignedNodeForwardServiceImpl(
            signProperties = signProperties,
            nodeService = nodeService,
            metadataService = metadataService,
            scanClient = scanClient,
            repositoryService = repositoryService,
            signConfigService = signConfigService
        )
}
