package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.pypi.util.DecompressUtil.getPkgInfo
import com.tencent.bkrepo.pypi.util.FileNameUtil.fileFormat
import com.tencent.bkrepo.pypi.util.pojo.PypiInfo

object ArtifactFileUtils {
    /**
     * 获取代理地址 pypi包信息
     */
    fun getPypiInfo(filename: String, artifactFile: ArtifactFile): PypiInfo {
        return filename.fileFormat().let { artifactFile.getInputStream().getPkgInfo(it) }
    }
}
