package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.generic.api.DownloadResource
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.service.DownloadService
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ResponseBody

/**
 * 通用文件下载服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-10-11
 */
@Controller
class DownloadResourceImpl @Autowired constructor(
    private val downloadService: DownloadService
) : DownloadResource {

    override fun simpleDownload(userId: String, artifactInfo: ArtifactInfo, request: HttpServletRequest, response: HttpServletResponse) {
        artifactInfo.run {
            downloadService.simpleDownload(userId, projectId, repoName, this.coordinate.fullPath, request, response)
        }
    }

    override fun blockDownload(userId: String, artifactInfo: ArtifactInfo, sequence: Int, request: HttpServletRequest, response: HttpServletResponse) {
        artifactInfo.run {
            downloadService.blockDownload(userId, projectId, repoName, this.coordinate.fullPath, sequence, request, response)
        }
    }

    @ResponseBody
    override fun queryBlockInfo(userId: String, artifactInfo: ArtifactInfo): Response<List<BlockInfo>> {
        return artifactInfo.run {
            Response.success(downloadService.queryBlockInfo(userId, projectId, repoName, this.coordinate.fullPath))
        }
    }
}
