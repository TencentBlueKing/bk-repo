/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlanGenerateParam
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanGenerator
import org.springframework.scheduling.support.CronExpression
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 用户自定义加载策略
 */
class CustomArtifactPreloadPlanGenerator : ArtifactPreloadPlanGenerator {
    override fun generate(param: ArtifactPreloadPlanGenerateParam): ArtifactPreloadPlan? {
        val now = LocalDateTime.now()
        val executeTime = CronExpression
            .parse(param.strategy.preloadCron!!)
            .next(now)
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
            ?: return null
        with(param) {
            return ArtifactPreloadPlan(
                id = null,
                createdDate = now,
                lastModifiedDate = now,
                strategyId = param.strategy.id!!,
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                sha256 = sha256,
                size = size,
                credentialsKey = credentialsKey,
                executeTime = executeTime
            )
        }
    }
}
