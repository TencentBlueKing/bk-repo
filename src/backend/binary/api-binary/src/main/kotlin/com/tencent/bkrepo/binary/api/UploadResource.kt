package com.tencent.bkrepo.binary.api

import com.tencent.bkrepo.binary.constant.SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 元数据服务接口
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@Api("上传接口")
@FeignClient(SERVICE_NAME, contextId = "UploadResource")
@RequestMapping("/user/binary")
interface UploadResource {

    @ApiOperation("say Hello")
    @GetMapping("/{name}")
    fun detail(
        @ApiParam(value = "姓名")
        @PathVariable name: String
    ): Response<String>

}
