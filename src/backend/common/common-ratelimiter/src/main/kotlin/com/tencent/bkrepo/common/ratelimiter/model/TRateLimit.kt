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

package com.tencent.bkrepo.common.ratelimiter.model

import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Duration

@Document(collection = "rate_limit")
@CompoundIndexes(
    CompoundIndex(
        name = "resource_limitDimension_idx",
        def = "{'resource': 1,'limitDimension': 1}",
        background = true
    ),
    CompoundIndex(
        name = "limitDimension_idx",
        def = "{'limitDimension': 1}",
        background = true
    )
)
data class TRateLimit(
    var id: String?,
    // 算法选择
    var algo: String = Algorithms.FIXED_WINDOW.name,
    // 资源标识
    var resource: String = "/",
    // 限流维度
    var limitDimension: String = LimitDimension.URL.name,
    // 限流值
    var limit: Long = -1,
    // 限流周期
    var duration: Duration = Duration.ofSeconds(1),
    // 桶容量(令牌桶和漏桶使用)
    var capacity: Long? = null,
    // 生效范围
    var scope: String = WorkScope.LOCAL.name,
    // 指定机器上运行
    var targets: List<String> = emptyList(),
    // 模块名
    var moduleName: List<String>,
    // 是否保持连接
    var keepConnection: Boolean = true,
)
