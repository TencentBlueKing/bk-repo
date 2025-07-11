/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.event.base

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * artifact抽象事件
 */
open class ArtifactEvent(
    /**
     * 事件类型
     */
    open val type: EventType,
    /**
     * 项目id
     */
    open val projectId: String,
    /**
     * 仓库名称
     */
    open val repoName: String,
    /**
     * 事件资源key，具有唯一性
     * ex:
     * 1. 节点类型对应fullPath
     * 2. 仓库类型对应仓库名称
     * 3. 包类型对应包名称
     */
    open val resourceKey: String,
    /**
     * 操作用户
     */
    open val userId: String,
    /**
     * 附属数据
     */
    open val data: Map<String, Any> = mapOf(),
    /**
     * 来源， 正常上传或者是联邦同步
     */
    open val source: String? = null,
) {
    override fun toString(): String {
        return "ArtifactEvent(type=$type, projectId='$projectId', repoName='$repoName', " +
            "resourceKey='$resourceKey', userId='$userId', data=$data)"
    }

    /**
     * 该类型消息对应的topic
     */
    open val topic: String = "bkrepo_artifact_" + type.name.split("_").first().toLowerCase()

    /**
     * 获取完整的资源key
     */
    @JsonIgnore
    fun getFullResourceKey(): String {
        return "$projectId/$repoName/$resourceKey"
    }
}
