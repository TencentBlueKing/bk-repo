package com.tencent.bkrepo.common.metadata.service.drive

import com.tencent.bkrepo.fs.server.pojo.DriveFileBlockInfo

interface DriveFileReadService {

    fun getFileBlockInfo(projectId: String, repoName: String, fullPath: String): DriveFileBlockInfo?
}
