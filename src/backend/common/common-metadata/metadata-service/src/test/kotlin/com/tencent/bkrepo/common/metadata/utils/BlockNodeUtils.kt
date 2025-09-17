package com.tencent.bkrepo.common.metadata.utils

import com.tencent.bkrepo.common.metadata.UT_CRC64_ECMA
import com.tencent.bkrepo.common.metadata.UT_PROJECT_ID
import com.tencent.bkrepo.common.metadata.UT_REPO_NAME
import com.tencent.bkrepo.common.metadata.UT_SHA256
import com.tencent.bkrepo.common.metadata.UT_USER
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import java.time.LocalDateTime

object BlockNodeUtils {
    fun buildBlockNode(
        projectId: String = UT_PROJECT_ID,
        repoName: String = UT_REPO_NAME,
        fullPath: String = "/a/b/c.txt",
    ) = TBlockNode(
        id = null,
        createdBy = UT_USER,
        createdDate = LocalDateTime.now(),
        nodeFullPath = fullPath,
        startPos = 0,
        sha256 = UT_SHA256,
        crc64ecma = UT_CRC64_ECMA,
        projectId = projectId,
        repoName = repoName,
        size = 1024L,
    )
}
