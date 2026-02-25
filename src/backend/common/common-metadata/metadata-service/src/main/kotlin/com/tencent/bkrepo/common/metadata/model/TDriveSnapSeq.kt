package com.tencent.bkrepo.common.metadata.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Drive 文件系统快照序列号计数器
 */
@Document("drive_snap_seq")
@CompoundIndex(name = "repo_idx", def = "{'projectId': 1, 'repoName': 1}", unique = true, background = true)
data class TDriveSnapSeq(
    var id: String? = null,
    var projectId: String,
    var repoName: String,

    /**
     * 当前快照序列号
     */
    var snapSeq: Long = 0,
)
