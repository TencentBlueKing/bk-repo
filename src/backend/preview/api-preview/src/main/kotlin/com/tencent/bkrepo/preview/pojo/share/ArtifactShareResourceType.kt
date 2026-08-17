package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 分享绑定的资源体系。本期仅写入 [DRIVE_NODE]；[NODE] 为经典 TNode 预留。
 */
@Schema(title = "作品分享资源类型")
enum class ArtifactShareResourceType {
    DRIVE_NODE,
    NODE,
}
