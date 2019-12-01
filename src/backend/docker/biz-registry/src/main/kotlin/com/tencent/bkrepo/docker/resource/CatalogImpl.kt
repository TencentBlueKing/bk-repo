package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Catalog
import com.tencent.bkrepo.docker.v2.rest.handler.DockerV2LocalRepoHandler
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CatalogImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoHandler) : Catalog {

    override fun list(n: Int?, last: String?): ResponseEntity<Any> {
        var maxEntries = 0
        var index = ""
        if (n != null) {
            maxEntries = n
        }
        if (last != null){
            index = last
        }
        return dockerRepo.catalog(maxEntries, index)
    }
}
