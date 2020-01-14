package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_ADD_USER_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_USER_LOGOUT_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_WHOAMI_MAPPING_URI
import com.tencent.bkrepo.npm.pojo.NpmAuthResponse
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

@Api("npm用户认证接口定义")
interface AuthResource {
    @ApiOperation("add user")
    @PutMapping(NPM_ADD_USER_MAPPING_URI)
    fun addUser(
        @RequestBody body: String
    ): NpmAuthResponse<String>

    @ApiOperation("user logout")
    @DeleteMapping(NPM_USER_LOGOUT_MAPPING_URI)
    fun logout(): NpmAuthResponse<Void>

    @ApiOperation("npm whoami")
    @GetMapping(NPM_WHOAMI_MAPPING_URI)
    fun whoami(): Map<String, String>
}