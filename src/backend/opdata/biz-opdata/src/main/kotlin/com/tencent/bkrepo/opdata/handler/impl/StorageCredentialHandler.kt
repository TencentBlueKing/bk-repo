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

package com.tencent.bkrepo.opdata.handler.impl

import com.tencent.bkrepo.opdata.handler.QueryHandler
import com.tencent.bkrepo.opdata.model.StorageCredentialsModel
import com.tencent.bkrepo.opdata.pojo.NodeResult
import com.tencent.bkrepo.opdata.pojo.Target
import com.tencent.bkrepo.opdata.pojo.enums.Metrics
import com.tencent.bkrepo.opdata.pojo.enums.StatMetrics
import org.springframework.stereotype.Component

/**
 * 依据存储实例的文件数量、大小统计
 */
@Component
class StorageCredentialHandler(
    private val storageCredentialsModel: StorageCredentialsModel
) : QueryHandler {

    override val metric: Metrics get() = Metrics.STORAGECREDENTIAL

    @Suppress("UNCHECKED_CAST")
    override fun handle(target: Target, result: MutableList<Any>) {
        val reqData = if (target.data is Map<*, *>) {
            target.data as Map<String, Any>
        } else {
            null
        }
        val metric = StatMetrics.valueOf(reqData?.get(STAT_METRICS) as? String ?: StatMetrics.NUM.name)
        val resultMap = storageCredentialsModel.getStorageCredentialsStat(metric)
        resultMap.forEach {
            val data = listOf(it.value, System.currentTimeMillis())
            val element = listOf(data)
            result.add(NodeResult(it.key, element))
        }
    }

    companion object {
        private const val STAT_METRICS = "metrics"
    }
}
