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

package com.tencent.bkrepo.job.migrate.utils

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.pojo.Node
import org.springframework.stereotype.Component
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
class TransferDataExecutor(
    private val properties: MigrateRepoStorageProperties
) {

    /**
     * 实际执行数据迁移的线程池
     */
    private val transferDataExecutor: ThreadPoolExecutor by lazy {
        buildExecutor(properties.nodeConcurrency, "transfer-data")
    }

    /**
     * 执行小文件迁移的线程池
     */
    private val transferSmallDataExecutor: ThreadPoolExecutor by lazy {
        buildExecutor(properties.smallNodeConcurrency, "transfer-data-small")
    }

    /**
     * 执行数据迁移
     *
     * @param node 待迁移的node
     * @param r 迁移动作
     */
    fun execute(node: Node, r: Runnable) {
        val smallExecutorProjects = properties.smallExecutorProjects
        val isSmallExecutorProject = smallExecutorProjects.isEmpty() || node.projectId in smallExecutorProjects
        if (isSmallExecutorProject && node.size < properties.smallNodeThreshold.toBytes()) {
            transferSmallDataExecutor.execute(r)
        } else {
            transferDataExecutor.execute(r)
        }
    }

    private fun buildExecutor(size: Int, prefix: String) = ThreadPoolExecutor(
        size,
        size,
        0L,
        TimeUnit.MILLISECONDS,
        SynchronousQueue(),
        ThreadFactoryBuilder().setNameFormat("$prefix-%d").build(),
        ThreadPoolExecutor.CallerRunsPolicy()
    )
}
