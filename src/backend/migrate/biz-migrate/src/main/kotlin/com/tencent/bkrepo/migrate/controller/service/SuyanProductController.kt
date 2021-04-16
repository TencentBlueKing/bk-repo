package com.tencent.bkrepo.migrate.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.migrate.api.SuyanProductClient
import com.tencent.bkrepo.migrate.pojo.BkProduct
import com.tencent.bkrepo.migrate.service.SuyanProductService
import org.springframework.web.bind.annotation.RestController

@RestController
class SuyanProductController(
    private val suyanProductService: SuyanProductService
) : SuyanProductClient {
    override fun listMavenProducts(
        repoName: String,
        groupId: String,
        artifactId: String,
        version: String,
        type: String
    ): Response<List<BkProduct>?> {
        val bkProductList = suyanProductService.findFirstByRepositoryNameAndGroupIdAndArtifactIdAndVersionAndType(
            repositoryName = repoName,
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            type = type
        )
        return ResponseBuilder.success(bkProductList)
    }
}
