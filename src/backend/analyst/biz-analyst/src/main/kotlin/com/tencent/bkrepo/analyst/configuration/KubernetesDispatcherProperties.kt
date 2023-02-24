/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties("scanner.dispatcher.k8s")
data class KubernetesDispatcherProperties(
    /**
     * 是否启用
     */
    var enabled: Boolean = false,
    /**
     * 创建job使用的命名空间
     */
    var namespace: String = "default",
    /**
     * k8s api server url
     */
    var apiServer: String? = null,
    /**
     * 用于访问apiServer时进行认证，未配置时取当前环境的~/.kube/config，或者当前部署的service account
     */
    var token: String? = null,
    /**
     * job执行结束后，k8s中job对象保留时间为一小时
     */
    var jobTtlSecondsAfterFinished: Int = 60 * 60,

    /**
     * 是否在执行成功后删除job，如果K8S集群的ttlSecondsAfterFinished参数可用，可将该参数设置为false
     */
    var cleanJobAfterSuccess: Boolean = true,

    /**
     * 容器limit mem
     */
    var limitMem: DataSize = DataSize.ofGigabytes(32),

    /**
     * 容器 request mem
     */
    var requestMem: DataSize = DataSize.ofGigabytes(16),

    /**
     * 会在文件三倍大小与该值之间取大者作为容器request ephemeralStorage
     */
    var requestStorage: DataSize = DataSize.ofGigabytes(16),

    /**
     * 容器limit ephemeralStorage
     */
    var limitStorage: DataSize = DataSize.ofGigabytes(128),

    /**
     * 容器request cpu
     */
    var requestCpu: Double = 4.0,
    /**
     * 容器limit cpu
     */
    var limitCpu: Double = 16.0
)
