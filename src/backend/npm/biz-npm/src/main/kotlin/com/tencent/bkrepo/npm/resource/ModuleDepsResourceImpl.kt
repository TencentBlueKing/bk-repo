package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.npm.api.ModuleDepsResource
import com.tencent.bkrepo.npm.pojo.module.des.service.DepsCreateRequest
import com.tencent.bkrepo.npm.pojo.module.des.service.DepsDeleteRequest
import com.tencent.bkrepo.npm.pojo.module.des.ModuleDepsInfo
import com.tencent.bkrepo.npm.service.ModuleDepsService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ModuleDepsResourceImpl @Autowired constructor(
    private val moduleDepsService: ModuleDepsService
) : ModuleDepsResource {
    override fun create(depsCreateRequest: DepsCreateRequest): Response<ModuleDepsInfo> {
        return ResponseBuilder.success(moduleDepsService.create(depsCreateRequest))
    }

    override fun batchCreate(depsCreateRequest: List<DepsCreateRequest>): Response<Void> {
        moduleDepsService.batchCreate(depsCreateRequest)
        return ResponseBuilder.success()
    }

    override fun delete(depsDeleteRequest: DepsDeleteRequest): Response<Void> {
        moduleDepsService.delete(depsDeleteRequest)
        return ResponseBuilder.success()
    }

    override fun deleteAllByName(depsDeleteRequest: DepsDeleteRequest): Response<Void> {
        moduleDepsService.deleteAllByName(depsDeleteRequest)
        return ResponseBuilder.success()
    }

    override fun find(projectId: String, repoName: String, name: String, deps: String): Response<ModuleDepsInfo> {
        return ResponseBuilder.success(moduleDepsService.find(projectId, repoName, name, deps))
    }

    override fun list(projectId: String, repoName: String, name: String): Response<List<ModuleDepsInfo>> {
        return ResponseBuilder.success(moduleDepsService.list(projectId, repoName, name))
    }

    override fun page(
        projectId: String,
        repoName: String,
        page: Int,
        size: Int,
        name: String
    ): Response<Page<ModuleDepsInfo>> {
        return ResponseBuilder.success(moduleDepsService.page(projectId, repoName, page, size, name))
    }
}
