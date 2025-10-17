package com.tencent.bkrepo.repository.pojo.blocknode.service

import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.AuditableRequest
import com.tencent.bkrepo.repository.pojo.ServiceRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRequest
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 创建block节点请求
 */
@Schema(title = "创建分块节点请求")
data class BlockNodeCreateRequest(
    @get:Schema(title = "所属项目", required = true)
    override val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    override val repoName: String,
    @get:Schema(title = "完整路径", required = true)
    override val fullPath: String,
    @get:Schema(title = "过期时间")
    var expireDate: LocalDateTime? = null,
    @get:Schema(title = "文件大小，单位byte")
    val size: Long,
    @get:Schema(title = "文件sha256")
    val sha256: String,
    @get:Schema(title = "文件crc64ecma")
    val crc64ecma: String? = null,
    @get:Schema(title = "分块在完整文件中的起始位置")
    val startPos: Long,
    @get:Schema(title = "分块在完整文件中的结束位置")
    val endPos: Long,
    @get:Schema(title = "操作来源,联邦仓库同步时源集群name", required = false)
    val source: String? = null,
    @get:Schema(title = "上传id")
    val uploadId: String? = null,
    @get:Schema(title = "删除时间")
    var deleted: String? = null,
    @get:Schema(title = "操作用户")
    override val operator: String = SYSTEM_USER,
    override val createdBy: String,
    override var createdDate: LocalDateTime,
    override val lastModifiedBy: String? = null,
    override val lastModifiedDate: LocalDateTime? = null,
) : NodeRequest, ServiceRequest, AuditableRequest
