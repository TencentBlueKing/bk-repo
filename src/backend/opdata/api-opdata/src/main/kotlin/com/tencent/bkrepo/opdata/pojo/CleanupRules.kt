/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.pojo

class CleanupRules(
    // 特殊项目仓库的保留周期
    var specialRepoRules: Map<String, String> = emptyMap(),
    // bg下仓库标准保留周期
    var bgRepoRules: Map<String, String> = emptyMap(),
    // 默认仓库名
    var defaultRepos: List<String> = emptyList(),
    // bg默认清理策略
    var cleanupType: String = "retentionDays",
    // bg默认清理时间
    var cleanupValue: String = "",
    // 清理策略是否开启
    var enable: Boolean = false,
    // 关联仓库（根据关联仓库去更新默认仓库清理策略）
    var relatedRepo: String = "",
    // 当开启后，对已经设置清理策略的仓库也进行策略更新
    var forceRefresh: Boolean = false,
)
