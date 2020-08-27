package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_USER_LOGOUT_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_WHOAMI_MAPPING_URI
import com.tencent.bkrepo.npm.pojo.auth.NpmAuthResponse
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute

@Api("npm用户认证接口定义")
interface AuthResource {
    @ApiOperation("user logout")
    @DeleteMapping(NPM_USER_LOGOUT_MAPPING_URI)
    fun logout(@RequestAttribute userId: String): NpmAuthResponse<Void>

    @ApiOperation("npm whoami")
    @GetMapping(NPM_WHOAMI_MAPPING_URI)
    fun whoami(@RequestAttribute userId: String): Map<String, String>
}
