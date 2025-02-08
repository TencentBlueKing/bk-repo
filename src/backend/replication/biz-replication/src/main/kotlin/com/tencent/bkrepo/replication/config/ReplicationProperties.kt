/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.config

import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties("replication")
data class ReplicationProperties(

    /**
     * 文件发送限速
     */
    var rateLimit: DataSize = DataSize.ofBytes(-1),

    /**
     * oci blob文件上传分块大小
     */
    var chunkedSize: Long = 1024 * 1024 * 5,
    /**
     * oci blob文件上传并发数
     */
    var threadNum: Int = 3,

    /**
     * manual分发并行数
     */
    var manualConcurrencyNum: Int = 3,

    /**
     * 签名过滤器body限制大小
     * */
    var bodyLimit: DataSize = DataSize.ofMegabytes(5),

    /**
     * 开启请求超时校验的域名以及对应平均速率（MB/s）
     * 配置如下：
     *   timoutCheckHosts
     *     - host: xx
     *       rate: x
     *     - host: xx
     *       rate: x
     */
    var timoutCheckHosts: List<Map<String, String>> = emptyList(),
    /**
     * 一次性查询的page size
     */
    var pageSize: Int = 500,
    /**
     * 集群间制品同步方式：
     * 追加上传：CHUNKED
     * 普通上传（单个请求）：DEFAULT
     * */
    var pushType: String = WayOfPushArtifact.PUSH_WITH_DEFAULT.value,
    /**
     * 使用追加上传项目
     * */
    var chunkedRepos: List<String> = emptyList(),
    /**
     * 使用fdtp上传项目
     * */
    var fdtpRepos: List<String> = emptyList(),
    /**
     * 使用http上传项目
     * */
    var httpRepos: List<String> = emptyList(),
    /**
     * 分发任务调度服务器所需账户密码
     */
    var dispatchUser: String? = null,
    var dispatchPwd: String? = null,
    /**
     * 针对部分 client_max_body_size 大小限制，
     * 导致超过该请求的文件无法使用普通上传
     */
    var clientMaxBodySize: Long = 10 * 1024 * 1024 * 1024L
    )
