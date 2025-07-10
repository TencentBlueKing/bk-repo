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

package com.tencent.bkrepo.job.migrate.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

@ConfigurationProperties("migrate")
data class MigrateRepoStorageProperties(
    /**
     * 允许同时迁移的制品数量
     */
    var nodeConcurrency: Int = Runtime.getRuntime().availableProcessors() * 2,

    /**
     * 允许同时迁移的小文件数量
     */
    var smallNodeConcurrency: Int = Runtime.getRuntime().availableProcessors() * 2,

    /**
     * 小于该大小的文件属于小文件
     */
    var smallNodeThreshold: DataSize = DataSize.ofMegabytes(1L),

    /**
     * 允许使用小文件迁移线程的项目,为空时所有项目可用
     */
    var smallExecutorProjects: Set<String> = emptySet(),

    /**
     * 更新进度间隔，指定每迁移多少个制品更新一次任务进度
     */
    var updateProgressInterval: Int = 10,
    /**
     * 从开始执行迁移任务到可执行数据矫正的时间间隔，用于等待传输中的制品完成传输，避免执行数据矫正时候还有数据在传输到旧存储中
     */
    var correctInterval: Duration = Duration.ofHours(4L),

    /**
     * 任务执行超时时间，超时后会检查任务是否被中断
     */
    var timeout: Duration = Duration.ofMinutes(10L),

    /**
     * 每秒允许迁移已归档文件的数量
     *
     * 由于迁移已归档文件实际为数据库操作，需要减小并发度，避免对数据库造成压力
     */
    var migrateArchivedFileRate: Double = 100.0,
)
