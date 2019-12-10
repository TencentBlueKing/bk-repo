package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.DownloadResource
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.service.DownloadService
import com.tencent.bkrepo.repository.api.NodeResource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 通用文件下载服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-10-11
 */
@RestController
class DownloadResourceImpl @Autowired constructor(
    private val downloadService: DownloadService,
    private val nodeResource: NodeResource
) : DownloadResource {

    override fun download(artifactInfo: GenericArtifactInfo) {
        downloadService.download(artifactInfo)
    }


    @GetMapping("/error")
    fun error(): Response<String> {
        val response = nodeResource.error()
        println(response.isOk())
        return response
    }

    @GetMapping("/error1")
    fun error1(): Response<String> {
        val response = nodeResource.error1()
        println(response.isOk())
        return response
    }

    @GetMapping("/success")
    fun success(): Response<String> {
        val response = nodeResource.success()
        println(response.isOk())
        return response
    }
}
