package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.DevopsResource
import com.tencent.bkrepo.generic.pojo.devops.ExternalUrlRequest
import com.tencent.bkrepo.generic.service.DevopsService
import com.tencent.bkrepo.generic.service.DownloadService
import com.tencent.bkrepo.generic.service.OperateService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class DevopsResourceImpl @Autowired constructor(
    private val devopsService: DevopsService,
    private val downloadService: DownloadService,
    private val operateService: OperateService
) : DevopsResource {
    override fun createExternalDownloadUrl(userId: String, request: ExternalUrlRequest): Response<String> {
        return Response(devopsService.createExternalDownloadUrl(userId, request))
    }

    override fun externalDownload(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        token: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        downloadService.externalDownload(
            userId = userId,
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            token = token,
            request = request,
            response = response
        )
    }
}
