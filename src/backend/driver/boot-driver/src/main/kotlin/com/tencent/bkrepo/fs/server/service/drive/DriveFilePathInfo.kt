package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode

/**
 * 按 ino 解析出的普通文件路径与节点（含元数据）。
 */
data class DriveFilePathInfo(
    val fullPath: String,
    val node: TDriveNode,
)
