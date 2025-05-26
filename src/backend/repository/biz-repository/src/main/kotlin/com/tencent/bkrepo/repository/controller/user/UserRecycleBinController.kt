package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.repository.service.recycle.RecycleBinService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("回收站用户接口")
@RestController
@RequestMapping("/api/recycle-bin")
class UserRecycleBinController(
    private val recycleBinService: RecycleBinService
) {
    @ApiOperation("从回收站删除")
    @Principal(PrincipalType.ADMIN)
    @DeleteMapping("/node/delete/{projectId}/{repoName}/**")
    fun irreversibleDelete(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestParam(required = true) deletedId: Long
    ) {
        recycleBinService.irreversibleDelete(artifactInfo, deletedId)
    }
}
