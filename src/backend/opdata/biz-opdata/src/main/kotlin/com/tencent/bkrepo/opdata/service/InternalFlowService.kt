/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.opdata.model.TInternalFlow
import com.tencent.bkrepo.opdata.pojo.InternalFlowRequest
import com.tencent.bkrepo.opdata.pojo.NameWithTag
import com.tencent.bkrepo.opdata.pojo.enums.LevelType
import com.tencent.bkrepo.opdata.repository.InternalFlowRepository
import org.springframework.stereotype.Service

/**
 * 内部流转服务
 */
@Service
class InternalFlowService(
    private val internalFlowRepository: InternalFlowRepository
) {

    /**
     * 根据级别查询去重后的名称列表及对应的tag
     * @param level 级别类型
     * @return 去重后的名称和tag列表（每个name只取第一个tag）
     */
    fun getDistinctNamesByLevel(level: LevelType): List<NameWithTag> {
        val flows = internalFlowRepository.findByLevel(level)
        // 按name分组，每个name只取第一个tag
        return flows.groupBy { it.name }
            .map { (name, flowList) ->
                NameWithTag(
                    name = name,
                    tag = flowList.first().tag
                )
            }.sortedBy { it.name }
    }

    /**
     * 根据名称查询关联节点
     * @param name 节点名称
     * @return 关联节点列表，包括当前节点、前驱节点和后继节点
     */
    fun getRelatedFlowsByName(name: String): List<TInternalFlow> {
        // 1. 查找当前节点（所有匹配该 name 的节点）
        val currentNodes = internalFlowRepository.findByName(name)
        return currentNodes
    }

    /**
     * 创建内部流转配置
     * @param request 内部流转请求
     * @return 创建后的内部流转对象
     */
    fun createInternalFlow(request: InternalFlowRequest): TInternalFlow {
        val flow = TInternalFlow(
            level = request.level,
            name = request.name,
            tag = request.tag,
            next = request.next,
            forward = request.forward
        )
        return internalFlowRepository.save(flow)
    }

    /**
     * 删除内部流转配置
     * @param id 流转配置ID
     */
    fun deleteInternalFlow(id: String) {
        internalFlowRepository.deleteById(id)
    }
}


