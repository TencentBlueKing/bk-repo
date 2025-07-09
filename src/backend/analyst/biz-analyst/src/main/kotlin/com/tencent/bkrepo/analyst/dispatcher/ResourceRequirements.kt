/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.pojo.execution.KubernetesExecutionClusterProperties
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import kotlin.math.min

/**
 * 执行制品分析任务所需的资源
 */
data class ResourceRequirements(
    val limitMem: Long,
    val requestMem: Long,
    val limitStorage: Long,
    val requestStorage: Long,
    val limitCpu: Double,
    val requestCpu: Double,
) {

    companion object {
        fun calculate(
            scanner: Scanner,
            cluster: KubernetesExecutionClusterProperties? = null
        ): ResourceRequirements {
            val clusterLimitMem = cluster?.limitMem ?: scanner.limitMem
            val clusterLimitStorage = cluster?.limitStorage ?: scanner.limitStorage
            val clusterLimitCpu = cluster?.limitCpu ?: scanner.limitCpu

            val limitMem = min(scanner.limitMem, clusterLimitMem)
            val requestMem = min(scanner.requestMem, limitMem)

            val limitStorage = min(scanner.limitStorage, clusterLimitStorage)
            val reqStorage = min(scanner.requestStorage, limitStorage)

            val limitCpu = min(scanner.limitCpu, clusterLimitCpu)
            val requestCpu = min(scanner.requestCpu, limitCpu)
            return ResourceRequirements(limitMem, requestMem, limitStorage, reqStorage, limitCpu, requestCpu)
        }
    }
}
