/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.pojo.node.service

import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.AuditableRequest
import com.tencent.bkrepo.repository.pojo.ServiceRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeRequest
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 创建节点请求
 */
@Schema(title = "创建节点请求")
data class NodeCreateRequest(
    @get:Schema(title = "所属项目", required = true)
    override val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    override val repoName: String,
    @get:Schema(title = "完整路径", required = true)
    override val fullPath: String,
    @get:Schema(title = "是否为文件夹", required = true)
    val folder: Boolean,
    @get:Schema(title = "过期时间，单位天(0代表永久保存)")
    val expires: Long = 0,
    @get:Schema(title = "是否覆盖")
    val overwrite: Boolean = false,
    @get:Schema(title = "文件大小，单位byte")
    val size: Long? = null,
    @get:Schema(title = "文件sha256")
    val sha256: String? = null,
    @get:Schema(title = "文件md5")
    val md5: String? = null,
    @get:Schema(title = "元数据信息")
    @Deprecated("仅用于兼容旧接口", replaceWith = ReplaceWith("nodeMetadata"))
    val metadata: Map<String, Any>? = null,
    @get:Schema(title = "元数据信息")
    val nodeMetadata: List<MetadataModel>? = null,
    @get:Schema(title = "操作用户")
    override val operator: String = SYSTEM_USER,
    override val createdBy: String? = null,
    override var createdDate: LocalDateTime? = null,
    override val lastModifiedBy: String? = null,
    override var lastModifiedDate: LocalDateTime? = null,
    @get:Schema(title = "是否SEPARATE_UPLOAD")
    val separate: Boolean = false,
    @get:Schema(title = "操作来源,联邦仓库同步时源集群name", required = false)
    val source: String? = null
) : NodeRequest, ServiceRequest, AuditableRequest
