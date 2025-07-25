/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties("repository")
data class RepositoryProperties(
    var deletedNodeReserveDays: Long = 14,
    var defaultStorageCredentialsKey: String? = null,
    var listCountLimit: Long = 100000L,
    var slowLogTimeThreshold: Long = 1_000,
    @NestedConfigurationProperty
    var job: RepoJobProperties = RepoJobProperties(),
    @NestedConfigurationProperty
    var repoStorageMapping: RepoStorageMapping = RepoStorageMapping(),
    var allowUserAddSystemMetadata: List<String> = emptyList(),
    var gitUrl: String = "",
    var svnUrl: String = "",
    /**
     * 用于验证bkci webhook签名
     */
    var bkciWebhookSecret: String = "",
    /**
     * 当目录节点上的num字段小于该值时，去db中实时count目录大小
     * 注意： 此配置的值要比listCountLimit大
     */
    var subNodeLimit: Long = 100000000L,
    /**
     * 是否返回真实项目启用禁用状态
     */
    var returnEnabled: Boolean = true,
    /**
     * 系统元数据标签
     */
    var systemMetadataLabels: List<String> = emptyList(),
)
