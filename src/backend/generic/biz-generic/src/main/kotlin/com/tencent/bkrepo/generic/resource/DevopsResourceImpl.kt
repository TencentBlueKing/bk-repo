package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.generic.api.DevopsResource
import com.tencent.bkrepo.generic.pojo.devops.ExternalUrlRequest
import com.tencent.bkrepo.generic.service.DevopsService
import com.tencent.bkrepo.generic.service.DownloadService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Controller
class DevopsResourceImpl @Autowired constructor(
    private val devopsService: DevopsService,
    private val downloadService: DownloadService
) : DevopsResource {
    override fun createExternalDownloadUrl(userId: String, externalUrlRequest: ExternalUrlRequest) {
        devopsService.createExternalDownloadUrl(userId, externalUrlRequest)
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
