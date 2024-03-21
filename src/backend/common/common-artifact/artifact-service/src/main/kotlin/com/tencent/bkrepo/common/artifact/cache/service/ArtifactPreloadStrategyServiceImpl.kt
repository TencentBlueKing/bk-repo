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

package com.tencent.bkrepo.common.artifact.cache.service

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode.PARAMETER_INVALID
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.dao.ArtifactPreloadStrategyDao
import com.tencent.bkrepo.common.artifact.cache.model.TArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategy.Companion.toDto
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyCreateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyUpdateRequest
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.ARTIFACT_PRELOAD_STRATEGY_EXCEED_MAX_COUNT
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.ARTIFACT_PRELOAD_STRATEGY_NOT_FOUND
import org.slf4j.LoggerFactory
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.regex.PatternSyntaxException

@Service
class ArtifactPreloadStrategyServiceImpl(
    private val artifactPreloadStrategyDao: ArtifactPreloadStrategyDao,
    private val preloadProperties: ArtifactPreloadProperties,
) : ArtifactPreloadStrategyService {
    override fun create(request: ArtifactPreloadStrategyCreateRequest): ArtifactPreloadStrategy {
        with(request) {
            check(fullPathRegex, recentSeconds, preloadCron)
            val now = LocalDateTime.now()
            if (artifactPreloadStrategyDao.count(projectId, repoName) > preloadProperties.maxStrategyCount) {
                throw ErrorCodeException(ARTIFACT_PRELOAD_STRATEGY_EXCEED_MAX_COUNT, preloadProperties.maxStrategyCount)
            }
            return artifactPreloadStrategyDao.insert(
                TArtifactPreloadStrategy(
                    createdBy = operator,
                    createdDate = now,
                    lastModifiedBy = operator,
                    lastModifiedDate = now,
                    projectId = projectId,
                    repoName = repoName,
                    fullPathRegex = fullPathRegex,
                    recentSeconds = recentSeconds,
                    preloadCron = preloadCron,
                    type = type
                )
            ).toDto()
        }
    }

    override fun update(request: ArtifactPreloadStrategyUpdateRequest): ArtifactPreloadStrategy {
        with(request) {
            check(fullPathRegex, recentSeconds, preloadCron)
            if (artifactPreloadStrategyDao.update(request).matchedCount == 0L) {
                throw ErrorCodeException(ARTIFACT_PRELOAD_STRATEGY_NOT_FOUND, id, status = HttpStatus.NOT_FOUND)
            }
            return artifactPreloadStrategyDao.findById(id)!!.toDto()
        }
    }

    override fun delete(projectId: String, repoName: String, id: String) {
        if (artifactPreloadStrategyDao.delete(projectId, repoName, id).deletedCount == 0L) {
            throw ErrorCodeException(ARTIFACT_PRELOAD_STRATEGY_NOT_FOUND, id, status = HttpStatus.NOT_FOUND)
        }
    }

    override fun list(projectId: String, repoName: String): List<ArtifactPreloadStrategy> {
        return artifactPreloadStrategyDao.list(projectId, repoName).map { it.toDto() }
    }

    private fun check(regex: String?, recentSeconds: Long?, cron: String?) {
        // check regex
        try {
            regex?.toPattern()
        } catch (e: PatternSyntaxException) {
            logger.warn("invalid regex", e)
            throw ErrorCodeException(PARAMETER_INVALID, regex.toString(), status = HttpStatus.BAD_REQUEST)
        }

        // check recentSeconds
        val maxSeconds = preloadProperties.maxArtifactExistsDuration.seconds
        if (recentSeconds != null && (recentSeconds <= 0L || recentSeconds > maxSeconds)) {
            throw ErrorCodeException(
                PARAMETER_INVALID, "$recentSeconds exceed max $maxSeconds", status = HttpStatus.BAD_REQUEST
            )
        }

        // check cron
        if (cron != null && !CronExpression.isValidExpression(cron)) {
            throw ErrorCodeException(PARAMETER_INVALID, cron, status = HttpStatus.BAD_REQUEST)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactPreloadStrategyServiceImpl::class.java)
    }
}
