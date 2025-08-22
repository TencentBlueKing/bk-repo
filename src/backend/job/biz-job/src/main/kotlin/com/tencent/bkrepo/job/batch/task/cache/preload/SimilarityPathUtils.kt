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

package com.tencent.bkrepo.job.batch.task.cache.preload

import com.tencent.bkrepo.auth.constant.PIPELINE

/**
 * 拼接projectId、repoName、fullPath用于向量化
 * 流水线仓库路径/p-xxx/b-xxx/xxx中的构建id为随机生成，不参与相似度计算
 */
fun projectRepoFullPath(projectId: String, repoName: String, fullPath: String): String {
    return if (repoName == PIPELINE) {
        // 流水线仓库路径/p-xxx/b-xxx/xxx中的构建id不参与相似度计算
        val secondSlashIndex = fullPath.indexOf("/", 1)
        val pipelinePath = fullPath.substring(0, secondSlashIndex)
        val artifactPath = fullPath.substring(fullPath.indexOf("/", secondSlashIndex + 1))
        "/$projectId/$repoName$pipelinePath$artifactPath"
    } else {
        "/$projectId/$repoName$fullPath"
    }
}
