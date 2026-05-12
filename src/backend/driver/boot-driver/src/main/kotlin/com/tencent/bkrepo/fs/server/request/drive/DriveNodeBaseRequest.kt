package com.tencent.bkrepo.fs.server.request.drive

/**
 * DriveNode创建和更新请求的公共基类
 * 字段统一为可空类型，以兼容UpdateRequest的可选更新语义
 * CreateRequest中非空字段通过override收窄类型
 */
abstract class DriveNodeBaseRequest(
    open val projectId: String,
    open val repoName: String,
    open val parent: Long? = null,
    open val name: String? = null,
    open val size: Long? = null,
    open val mode: Int? = null,
    open val nlink: Int? = null,
    open val uid: Int? = null,
    open val gid: Int? = null,
    open val rdev: Int? = null,
    open val flags: Int? = null,
    open val symlinkTarget: String? = null,
)
