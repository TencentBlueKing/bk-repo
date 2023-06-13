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

package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.replication.pojo.dispatch.request.ReplicaNodeDispatchConfigRequest
import com.tencent.bkrepo.replication.pojo.dispatch.ReplicaNodeDispatchConfigInfo

/**
 * 分发任务执行服务器对应调度逻辑处理接口
 */
interface ReplicaNodeDispatchService {


    /**
     * 创建分发服务器调度配置
     */
    fun createReplicaNodeDispatchConfig(request: ReplicaNodeDispatchConfigRequest)

    /**
     * 获取所有分发服务器调度配置
     */
    fun listAllReplicaNodeDispatchConfig(): List<ReplicaNodeDispatchConfigInfo>

    /**
     * 根据属性以及对应配置规则查询配置的分发服务器调度配置
     */
    fun getReplicaNodeDispatchConfig(ruleIndex: String, ruleType: OperationType): List<ReplicaNodeDispatchConfigInfo>


    /**
     * 根据分发目标host获取对应的分发调度服务client信息
     */
    fun <T> findReplicaClientByTargetHost(url: String, target: Class<T>): T?
}

