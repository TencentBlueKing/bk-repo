package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.service.StageService
import org.springframework.web.bind.annotation.RestController

/**
 * 晋级服务接口实现类
 */
@RestController
class StageController(
    private val stageService: StageService
) : StageClient {

    override fun query(projectId: String, repoName: String, packageKey: String, version: String): Response<List<String>> {
        val tagList = stageService.query(projectId, repoName, packageKey, version)
        return ResponseBuilder.success(tagList)
    }

}
