package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.*
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@Api(tags = ["SERVICE_ROLE"], description = "服务-角色接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceRoleResource")
@RequestMapping("/role")
interface ServiceRoleResource {
    @ApiOperation("创建角色")
    @PostMapping("/create")
    fun createRole(
        @RequestBody request: CreateRoleRequest
    ): Response<String?>

    @ApiOperation("删除角色")
    @DeleteMapping("/delete/id")
    fun deleteRole(
        @ApiParam(value = "角色主键id")
        @PathVariable id: String
    ): Response<Boolean>


    @ApiOperation("根据类型查询角色")
    @GetMapping("/detail/{id}")
    fun detail(
        @ApiParam(value = "角色主键id")
        @PathVariable id: String
    ): Response<Role?>

    @ApiOperation("根据类型和项目id查询角色")
    @GetMapping("/list")
    fun listRoleByTypeAndProjectId(
        @ApiParam(value = "角色类型")
        @RequestParam type: RoleType?=null,
        @ApiParam(value = "项目ID")
        @RequestParam projectId: String?=null
    ): Response<List<Role>>
}
