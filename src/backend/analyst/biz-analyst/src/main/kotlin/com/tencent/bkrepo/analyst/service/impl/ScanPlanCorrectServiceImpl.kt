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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.model.TScanPlan
import com.tencent.bkrepo.analyst.service.ScanPlanCorrectService
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.query.model.PageLimit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ScanPlanCorrectServiceImpl(
    private val scanPlanDao: ScanPlanDao,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao
) : ScanPlanCorrectService {

    @Async
    override fun correctPlanOverview(planId: String?) {
        if (planId != null) {
            scanPlanDao.findById(planId)?.let { correct(it) }
        } else {
            var plans: List<TScanPlan>
            var pageNumber = DEFAULT_PAGE_NUMBER
            do {
                plans = scanPlanDao.page(PageLimit(pageNumber))
                plans.forEach { correct(it) }
                pageNumber++
            } while (plans.isNotEmpty())
        }
    }

    /**
     * 对扫描方案的预览数据进行矫正
     *
     * @param plan 扫描方案
     */
    private fun correct(plan: TScanPlan) {
        val planId = plan.id!!
        logger.info("start correct plan[$planId] overview data")

        var pageNumber = DEFAULT_PAGE_NUMBER
        var tasks: List<TPlanArtifactLatestSubScanTask>
        val overview = plan.scanResultOverview.mapValuesTo(HashMap()) { 0L }
        do {
            tasks = planArtifactLatestSubScanTaskDao.pageByPlanId(planId, PageLimit(pageNumber))
            tasks.forEach {
                it.scanResultOverview?.forEach { (key, value) ->
                    overview[key] = overview.getOrDefault(key, 0L) + value.toLong()
                }
            }
            pageNumber++
        } while (tasks.isNotEmpty())

        val corrected = overview.all { it.value == plan.scanResultOverview[it.key] }
        if (overview.size == plan.scanResultOverview.size && corrected) {
            logger.info("plan[$planId] overview data already corrected")
            return
        }

        scanPlanDao.setScanResultOverview(planId, overview)
        logger.info("correct plan[$planId] overview data success")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScanPlanCorrectServiceImpl::class.java)
    }
}
