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

import com.tencent.bkrepo.common.artifact.cache.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.cache.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlanGenerateParam
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.pojo.PreloadStrategyType
import com.tencent.bkrepo.common.metadata.constant.SYSTEM_USER
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class CustomArtifactPreloadPlanGeneratorTest {
    @Test
    fun testPlanExecuteTime() {
        val generator = CustomArtifactPreloadPlanGenerator()
        val now = LocalDateTime.now()
        val strategy = ArtifactPreloadStrategy(
            id = "",
            createdBy = SYSTEM_USER,
            createdDate = now,
            lastModifiedBy = SYSTEM_USER,
            lastModifiedDate = now,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPathRegex = "/a/b/.*\\.exe",
            minSize = 0L,
            recentSeconds = Duration.ofDays(7L).seconds,
            preloadCron = "0 0 0/1 * * ?",
            type = PreloadStrategyType.CUSTOM.name,
        )
        val param = ArtifactPreloadPlanGenerateParam(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            credentialsKey = null,
            fullPath = "/a/b/c.exe",
            sha256 = "0000000000000000000000000000000000000000000000000000000000000001",
            size = 1000L,
            strategy = strategy,
        )
        val plan = generator.generate(param)
        val expectedExecuteTime = now
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .plusHours(1L)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        Assertions.assertEquals(expectedExecuteTime, plan!!.executeTime)
    }
}
