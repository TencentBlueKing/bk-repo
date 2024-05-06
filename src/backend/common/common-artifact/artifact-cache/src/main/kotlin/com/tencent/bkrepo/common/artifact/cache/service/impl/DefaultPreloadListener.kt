/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.cache.service.impl

import com.tencent.bkrepo.common.artifact.cache.dao.ArtifactPreloadPlanDao
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan.Companion.STATUS_EXECUTING
import com.tencent.bkrepo.common.artifact.cache.service.PreloadListener

class DefaultPreloadListener(private val preloadPlanDao: ArtifactPreloadPlanDao) : PreloadListener {
    override fun onPreloadStart(plan: ArtifactPreloadPlan) {
        // 使用乐观锁尝试更新计划执行状态
        if (preloadPlanDao.updateStatus(plan.id!!, STATUS_EXECUTING, plan.lastModifiedDate).modifiedCount != 1L) {
            throw RuntimeException("update plan status failed, maybe plan was executed by other thread")
        }
    }

    override fun onPreloadSuccess(plan: ArtifactPreloadPlan) = Unit
    override fun onPreloadFailed(plan: ArtifactPreloadPlan) = Unit

    override fun onPreloadFinished(plan: ArtifactPreloadPlan) {
        // 执行结束后删除预加载计划
        preloadPlanDao.removeById(plan.id!!)
    }
}
