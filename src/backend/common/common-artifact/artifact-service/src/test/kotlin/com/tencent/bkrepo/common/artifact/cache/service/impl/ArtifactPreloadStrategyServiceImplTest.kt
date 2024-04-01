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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.cache.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.cache.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadConfiguration
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.dao.ArtifactPreloadStrategyDao
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyCreateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyUpdateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.PreloadStrategyType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import java.time.Duration

@DisplayName("预加载策略服务测试")
@DataMongoTest
@ImportAutoConfiguration(ArtifactPreloadConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties", "classpath:application-test.properties"])
class ArtifactPreloadStrategyServiceImplTest @Autowired constructor(
    private val artifactPreloadStrategyService: ArtifactPreloadStrategyServiceImpl,
    private val preloadProperties: ArtifactPreloadProperties,
    private val artifactPreloadStrategyDao: ArtifactPreloadStrategyDao,
) {
    @BeforeEach
    fun before() {
        // remove all
        artifactPreloadStrategyDao.remove(Query())
    }

    @Test
    fun testCreate() {
        val strategy = create()
        Assertions.assertEquals(UT_PROJECT_ID, strategy.projectId)
        Assertions.assertEquals(UT_REPO_NAME, strategy.repoName)
        Assertions.assertEquals(".*", strategy.fullPathRegex)
        Assertions.assertEquals(0L, strategy.minSize)
        Assertions.assertEquals(3600L, strategy.recentSeconds)
        Assertions.assertEquals("0 0 0 * * ?", strategy.preloadCron)
        Assertions.assertEquals(PreloadStrategyType.CUSTOM.name, strategy.type)

        // exceed max count
        assertThrows<ErrorCodeException> { repeat(preloadProperties.maxStrategyCount + 1) { create() } }
    }

    @Test
    fun testUpdate() {
        val strategy = create()
        val request = ArtifactPreloadStrategyUpdateRequest(
            id = strategy.id!!,
            projectId = strategy.projectId,
            repoName = strategy.repoName,
            fullPathRegex = ".*\\.jar",
            minSize = 0L,
            recentSeconds = 1000L,
            preloadCron = "0 0 1 * * ?"
        )
        val updatedStrategy = artifactPreloadStrategyService.update(request)
        Assertions.assertEquals(".*\\.jar", updatedStrategy.fullPathRegex)
        Assertions.assertEquals(1000L, updatedStrategy.recentSeconds)
        Assertions.assertEquals("0 0 1 * * ?", updatedStrategy.preloadCron)

        // invalid parameter
        assertThrows<ErrorCodeException> {
            artifactPreloadStrategyService.update(request.copy(recentSeconds = -1L))
        }
        assertThrows<ErrorCodeException> {
            artifactPreloadStrategyService.update(request.copy(recentSeconds = Duration.ofDays(8L).seconds))
        }
        assertThrows<ErrorCodeException> {
            artifactPreloadStrategyService.update(request.copy(preloadCron = "0 x 0 * * ?"))
        }
        assertThrows<ErrorCodeException> {
            artifactPreloadStrategyService.update(request.copy(fullPathRegex = "[0-9"))
        }
        assertThrows<ErrorCodeException> {
            artifactPreloadStrategyService.update(request.copy(minSize = -1L))
        }
    }

    @Test
    fun testDelete() {
        val strategy = create()
        with(strategy) {
            Assertions.assertEquals(1, artifactPreloadStrategyService.list(projectId, repoName).size)
            artifactPreloadStrategyService.delete(projectId, repoName, id!!)
            Assertions.assertEquals(0, artifactPreloadStrategyService.list(projectId, repoName).size)
        }
    }

    private fun create(): ArtifactPreloadStrategy {
        val request = ArtifactPreloadStrategyCreateRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPathRegex = ".*",
            minSize = 0L,
            recentSeconds = 3600L,
            preloadCron = "0 0 0 * * ?",
            type = PreloadStrategyType.CUSTOM.name
        )
        return artifactPreloadStrategyService.create(request)
    }
}
