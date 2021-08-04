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

package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.opdata.constant.B_0
import com.tencent.bkrepo.opdata.constant.GB_1
import com.tencent.bkrepo.opdata.constant.GB_10
import com.tencent.bkrepo.opdata.constant.MB_100
import com.tencent.bkrepo.opdata.constant.MB_500
import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_METRICS
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service

@Service
class ProjectMetricsModel @Autowired constructor(
    private var mongoTemplate: MongoTemplate
) {

    /**
     * 获取总体的文件大小分布
     */
    fun getSizeDistribution(): Map<String, Long> {
        val projectMetricsList = mongoTemplate.findAll(TProjectMetrics::class.java, OPDATA_PROJECT_METRICS)
        var zeroNum = 0L
        var mb100Num = 0L
        var mb500Num = 0L
        var gb1Num = 0L
        var gb10Num = 0L
        projectMetricsList.forEach {
            zeroNum += it.sizeDistribution[B_0] ?: 0
            mb100Num += it.sizeDistribution[MB_100] ?: 0
            mb500Num += it.sizeDistribution[MB_500] ?: 0
            gb1Num += it.sizeDistribution[GB_1] ?: 0
            gb10Num += it.sizeDistribution[GB_10] ?: 0
        }

        return mapOf(
            B_0 to zeroNum,
            MB_100 to mb100Num,
            MB_500 to mb500Num,
            GB_1 to gb1Num,
            GB_10 to gb10Num
        )
    }

    /**
     * 获取一个项目的文件大小分布
     */
    fun getProjSizeDistribution(projectId: String): Map<String, Long> {
        val query = Query(where(TProjectMetrics::projectId).isEqualTo(projectId))
        val projectMetrics = mongoTemplate.findOne(query, TProjectMetrics::class.java, OPDATA_PROJECT_METRICS)
        return projectMetrics?.sizeDistribution ?: mapOf()
    }

}
