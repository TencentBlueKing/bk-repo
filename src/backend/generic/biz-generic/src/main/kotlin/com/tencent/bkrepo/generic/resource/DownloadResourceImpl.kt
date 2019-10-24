package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
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
    override fun simpleDownload(userId: String, projectId: String, repoName: String, fullPath: String, request: HttpServletRequest, response: HttpServletResponse) {
        downloadService.simpleDownload(userId, projectId, repoName, fullPath, request, response)
    }

    override fun blockDownload(userId: String, projectId: String, repoName: String, fullPath: String, sequence: Int, request: HttpServletRequest, response: HttpServletResponse) {
        downloadService.blockDownload(userId, projectId, repoName, fullPath, sequence, request, response)
    }

    @ResponseBody
    override fun queryBlockInfo(userId: String, projectId: String, repoName: String, fullPath: String): Response<List<BlockInfo>> {
        return Response.success(downloadService.queryBlockInfo(userId, projectId, repoName, fullPath))
    }
}
