package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.constant.REPOSITORY_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(REPOSITORY_SERVICE_NAME, contextId = "FileReferenceClient")
@RequestMapping("/service/fileReference")
interface FileReferenceClient {

    @PutMapping("/decrement")
    fun decrement(@RequestParam sha256: String, @RequestParam credentialsKey: String?): Response<Boolean>

    @PutMapping("/increment")
    fun increment(@RequestParam sha256: String, @RequestParam credentialsKey: String?): Response<Boolean>

    @GetMapping("/count")
    fun count(@RequestParam sha256: String, @RequestParam credentialsKey: String?): Response<Long>
}
