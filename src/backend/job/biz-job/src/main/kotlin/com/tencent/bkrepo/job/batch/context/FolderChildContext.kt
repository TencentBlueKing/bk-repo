/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.job.MEMORY_CACHE_TYPE
import com.tencent.bkrepo.job.batch.base.ChildJobContext
import com.tencent.bkrepo.job.batch.base.JobContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

class FolderChildContext(
    parentContent: JobContext,
    // 是否执行任务
    var runFlag: Boolean = false,
    // 缓存类型redis和内存：数据量级大的建议使用redis
    var cacheType: String = MEMORY_CACHE_TYPE,
    // 表对应项目记录： 主要用于redis缓存生成key使用
    var projectMap: ConcurrentHashMap<String, MutableSet<String>> = ConcurrentHashMap(),
    // 用于内存缓存下存储目录统计信息
    var folderCache: ConcurrentHashMap<String, FolderMetrics> = ConcurrentHashMap(),
    var activeProjects: Set<String> = emptySet()
) : ChildJobContext(parentContent) {

    data class FolderMetrics(
        var nodeNum: LongAdder = LongAdder(),
        var capSize: LongAdder = LongAdder()
    )
}