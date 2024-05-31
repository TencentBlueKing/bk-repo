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

import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanService
import com.tencent.bkrepo.common.storage.core.cache.event.CacheFileDeletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 缓存文件相关事件监听器
 */
class CacheFileEventListener(
    private val properties: ArtifactPreloadProperties,
    private val preloadPlanService: ArtifactPreloadPlanService,
) {

    /**
     * 缓存被删除时判断是否需要创建预加载执行计划
     */
    @Async
    @EventListener(CacheFileDeletedEvent::class)
    fun onCacheFileDeleted(event: CacheFileDeletedEvent) {
        if (properties.enabled && event.data.size >= properties.minSize.toBytes()) {
            with(event.data) {
                logger.info("try generate preload plan for sha256[${sha256}], fullPath[$fullPath], size[$size")
                preloadPlanService.generatePlan(credentials.key, sha256)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CacheFileEventListener::class.java)
    }
}
