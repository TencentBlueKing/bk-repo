package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.generic.api.DownloadResource
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.service.DownloadService
import org.springframework.beans.factory.annotation.Autowired
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

    override fun download(artifactInfo: GenericArtifactInfo) {
        downloadService.download(artifactInfo)
    }
}