package com.tencent.bkrepo.fs.server.pojo

import com.tencent.bkrepo.common.storage.pojo.RegionResource

/**
 * Drive 文件块元数据，供 preview 等微服务通过 Feign 拉取后本地读存储。
 */
data class DriveFileBlockInfo(
    val fullPath: String,
    val fileName: String,
    val size: Long,
    val blocks: List<RegionResource>,
)
