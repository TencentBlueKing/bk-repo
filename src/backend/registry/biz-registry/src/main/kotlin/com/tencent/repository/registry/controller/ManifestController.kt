package com.tencent.bkrepo.registry.controller

import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class ManifestController {

    @PutMapping("/v2/{name}/manifests/{reference}")
    fun putManifest(
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String,
        @PathVariable
        @ApiParam(value = "reference", required = true)
        reference: String,
        @ApiParam
        @RequestHeader(value = "Content-Type", required = true)
        mediaType: String
    ): String {

        return "Hello, $name, $reference ,$mediaType!"
    }
}
