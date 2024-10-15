/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("artifact.event")
data class ArtifactEventProperties(
    // 是否更新节点访问时间
    var updateAccessDate: Boolean = false,
    // 不更新节点访问时间的项目仓库
    var filterProjectRepoKey: List<String> = emptyList(),
    // 是否上报节点更新访问时间事件
    var reportAccessDateEvent: Boolean = false,
    // 导出到消息队列时的topic
    var topic: String? = null,
    // 更新访问时间频率， 当时间间隔小于该值时不更新, 默认1天
    var accessDateDuration: Duration = Duration.ofDays(1),
    // 是否消费上报节点更新访问时间事件去更新对应访问时间
    var consumeAccessDateEvent: Boolean = false,
    // 更新节点访问时间的项目仓库, 默认为空，当为空的情况下更新所有事件
    var consumeProjectRepoKey: List<String> = emptyList(),
)
