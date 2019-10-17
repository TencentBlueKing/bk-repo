package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.DownloadResource
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.service.DownloadService
import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

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
    override fun simpleDownload(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse): ResponseEntity<InputStreamResource> {
        return downloadService.simpleDownload(userId, projectId, repoName, fullPath, response)
    }

    override fun queryBlockInfo(userId: String, projectId: String, repoName: String, fullPath: String): Response<List<BlockInfo>> {
        return Response.success(downloadService.queryBlockInfo(userId, projectId, repoName, fullPath))
    }

    override fun blockDownload(userId: String, projectId: String, repoName: String, fullPath: String, sequence: Int, response: HttpServletResponse): ResponseEntity<InputStreamResource> {
        return downloadService.blockDownload(userId, projectId, repoName, fullPath, sequence, response)
    }
}
