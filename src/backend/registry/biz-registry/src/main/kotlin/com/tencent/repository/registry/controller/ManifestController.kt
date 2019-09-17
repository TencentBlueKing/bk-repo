package com.tencent.bkrepo.registry.controller

import io.swagger.annotations.ApiParam
import okhttp3.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

// PutManifest validates and stores a manifest in the registry
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
        mediaType: String,
        @RequestBody
        @ApiParam(value = "body", required = false)
        body: String?
    ): String {
        var contentTypeHeader = MediaType.parse(mediaType).toString()
        return "Hello, $name, $reference ,$mediaType，$body"
    }
}
