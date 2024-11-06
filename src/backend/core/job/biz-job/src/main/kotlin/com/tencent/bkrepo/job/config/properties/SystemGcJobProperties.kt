/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties(value = "job.system-gc")
data class SystemGcJobProperties(
    override var cron: String = "0 0 0 * * ?",
    /**
     * 处理文件大小阈值
     * */
    var fileSizeThreshold: DataSize = DataSize.ofMegabytes(100),
    /**
     * 仓库信息
     * project/repo
     * */
    var repos: Set<String> = emptySet(),
    /**
     * 编辑距离阈值
     * */
    var edThreshold: Double = 0.3,
    /**
     * 保留最新次数
     * */
    var retain: Int = 3,

    /**
     * 访问时间限制
     * */
    var idleDays: Int = 30,

    /**
     * 最大批处理数量
     * */
    var maxBatchSize: Int = 10000,

    /**
     * 只有超过该节点数量，才会进行gc
     * */
    var nodeLimit: Int = 1000,
    /**
     * 最大的采样数量
     * */
    var maxSampleNum: Int = 10000,
) : MongodbJobProperties()
