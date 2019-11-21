package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactCoordinate
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

    override fun simpleDownload(userId: String, artifactCoordinate: ArtifactCoordinate, request: HttpServletRequest, response: HttpServletResponse) {
        artifactCoordinate.run {
            downloadService.simpleDownload(userId, projectId, repoName, artifactPath.fullPath, request, response)
        }
    }

    override fun blockDownload(userId: String, artifactCoordinate: ArtifactCoordinate, sequence: Int, request: HttpServletRequest, response: HttpServletResponse) {
        artifactCoordinate.run {
            downloadService.blockDownload(userId, projectId, repoName, artifactPath.fullPath, sequence, request, response)
        }
    }

    @ResponseBody
    override fun queryBlockInfo(userId: String, artifactCoordinate: ArtifactCoordinate): Response<List<BlockInfo>> {
        return artifactCoordinate.run {
            Response.success(downloadService.queryBlockInfo(userId, projectId, repoName, artifactPath.fullPath))
        }
    }
}
