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

package com.tencent.bkrepo.job.batch.task.cache.preload.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties("spring.ai")
data class AiProperties(
    /**
     * 向量化服务URL
     * 服务需要实现[com.tencent.bkrepo.job.batch.task.cache.preload.ai.HttpEmbeddingModel]中调用的接口
     */
    var embeddingServiceUrl: String = "",
    /**
     * 向量化服务token，会在请求头Authorization中携带用于认证
     */
    var embeddingServiceToken: String = "",
    /**
     * 模型维度
     */
    var dimenssion: Int = 384,
    /**
     * 调用向量化接口超时时间
     */
    var embeddingTimeout: Duration = Duration.ofMinutes(1L),
    /**
     * 向量数据库名
     */
    var databaseName: String = "default",
    /**
     * 向量数据库表名前缀
     */
    var collectionPrefix: String = "artifact_access_log_",
    /**
     * 默认相似度阈值，用于过滤相似路径查询结果
     */
    var defaultSimilarityThreshold: Double = 0.95,
)
