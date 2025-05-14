package com.tencent.bkrepo.git.controller

import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RequestMapping("{projectId}/{repoName}.git")
@RestController
class GitLfsController {

    @PostMapping("/info/lfs/objects/batch")
    fun batch(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ): Response<Void> {
        throw MethodNotAllowedException()
    }

    @GetMapping()
    @RequestMapping("/content/lfs/objects/{oid}", method = [RequestMethod.GET, RequestMethod.PUT])
    fun get(@PathVariable oid: String): Response<Void> {
        throw MethodNotAllowedException()
    }
}
