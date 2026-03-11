package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.AUTH_SERVICE_KEY_PREFIX
import com.tencent.bkrepo.auth.pojo.key.KeyInfo
import com.tencent.bkrepo.common.api.constant.AUTH_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "SERVICE_KEY", description = "服务-密钥接口")
@Primary
@FeignClient(AUTH_SERVICE_NAME, contextId = "ServiceKeyResource")
@RequestMapping(AUTH_SERVICE_KEY_PREFIX)
interface ServiceKeyClient {

    @Operation(summary = "查询指定用户的密钥列表（联邦同步）")
    @GetMapping("/list")
    fun listKeyByUserId(
        @RequestParam userId: String
    ): Response<List<KeyInfo>>

    @Operation(summary = "创建密钥（联邦同步）")
    @PostMapping("/create")
    fun createKeyForFederation(@RequestBody request: KeyInfo): Response<Boolean>

    @Operation(summary = "删除密钥（联邦同步）")
    @DeleteMapping("/delete/{id}")
    fun deleteKeyForFederation(@PathVariable id: String): Response<Boolean>
}
