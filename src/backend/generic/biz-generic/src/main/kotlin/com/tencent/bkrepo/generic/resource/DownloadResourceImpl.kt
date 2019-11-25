package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable.Companion.ARTIFACT_COORDINATE_URI
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.generic.api.DownloadResource
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.service.DownloadService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse

/**
 * 通用文件下载服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-10-11
 */
@RestController
class DownloadResourceImpl @Autowired constructor(
    private val downloadService: DownloadService
) : DownloadResource {

    override fun simpleDownload(userId: String, artifactInfo: ArtifactInfo) {
        downloadService.simpleDownload(userId, artifactInfo)
    }

    override fun blockDownload(userId: String, artifactInfo: ArtifactInfo, sequence: Int) {
        downloadService.blockDownload(userId, artifactInfo, sequence)
    }

    @ResponseBody
    override fun queryBlockInfo(userId: String, artifactInfo: ArtifactInfo): Response<List<BlockInfo>> {
        return Response.success(downloadService.queryBlockInfo(userId, artifactInfo))
    }
}