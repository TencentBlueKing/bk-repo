/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.opdata.handler

import com.tencent.bkrepo.opdata.constant.OPDATA_STAT_LIMIT
import com.tencent.bkrepo.opdata.constant.TOP_VALUE
import com.tencent.bkrepo.opdata.pojo.NodeResult
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.Metrics

interface QueryHandler {

    val metric: Metrics

    fun handle(target: Target, result: MutableList<Any>): Any


    fun getTopValue(target: Target, defaultTop: Int = OPDATA_STAT_LIMIT): Int {
        val reqData = if (target.data is Map<*, *>) {
            target.data as Map<String, Any>
        } else {
            null
        }
        return reqData?.get(TOP_VALUE)?.toString()?.toIntOrNull() ?: defaultTop
    }

    fun convToDisplayData(
        mapData: Map<String, Long>,
        result: MutableList<Any>,
        top: Int
    ): List<Any> {
        if (mapData.isEmpty()) return result
        val max = if (mapData.size > top) {
            top
        } else {
            mapData.size
        }
        val allNegative = mapData.all { it.value < 0 }
        val list = if (allNegative) {
            mapData.toList().sortedBy { it.second }
        } else {
            mapData.toList().sortedByDescending { it.second }
        }
        list.subList(0, max).forEach {
            val projectId = it.first
            val data = listOf(it.second, System.currentTimeMillis())
            val element = listOf(data)
            result.add(NodeResult(projectId, element))
        }
        return result
    }
}
