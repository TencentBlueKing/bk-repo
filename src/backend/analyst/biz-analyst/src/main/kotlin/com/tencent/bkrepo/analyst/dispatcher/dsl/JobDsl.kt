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

package com.tencent.bkrepo.analyst.dispatcher.dsl

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobSpec
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1PodTemplateSpec

/**
 * 创建job并配置
 */
fun v1Job(configuration: V1Job.() -> Unit): V1Job {
    return V1Job().apply(configuration)
}

/**
 * 配置Job元数据
 */
fun V1Job.metadata(configuration: V1ObjectMeta.() -> Unit) {
    if (metadata == null) {
        metadata = V1ObjectMeta()
    }
    metadata!!.configuration()
}

/**
 * 配置Job执行方式
 */
fun V1Job.spec(configuration: V1JobSpec.() -> Unit) {
    if (spec == null) {
        spec = V1JobSpec()
    }
    spec!!.configuration()
}

/**
 * 配置Job用于创建Pod的模板
 */
fun V1JobSpec.template(configuration: V1PodTemplateSpec.() -> Unit) {
    if (template == null) {
        template = V1PodTemplateSpec()
    }
    template.configuration()
}
