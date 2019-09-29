package com.tencent.bkrepo.registry.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.registry.manifest2.ManifestDeserializer
import com.tencent.bkrepo.registry.manifest2.ManifestHandler
import com.tencent.bkrepo.registry.util.Digest
import io.swagger.annotations.ApiParam
import okhttp3.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

// PutManifest validates and stores a manifest in the registry
@RestController
class ManifestController @Autowired constructor(val objectMapper: ObjectMapper) {

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
        contentTypeHeader: String,
        @RequestBody
        @ApiParam(value = "body", required = false)
        body: String
    ): String {
        var manHandle = ManifestHandler("", "")
        var mediaType = MediaType.parse(contentTypeHeader).toString()
        var isDigest = Digest.validDigest(reference)
        var desc = ManifestDeserializer.deserialize(mediaType, body.toByteArray())
        if (isDigest) {
            manHandle.digest = reference
            if (manHandle.digest != desc.digest) {
                return "error"
            }
        } else {
            manHandle.digest = desc.digest
            manHandle.tag = reference
        }

        // Tag this manifest

        // Construct a canonical url for the uploaded manifest

        return "Hello, $name, $reference ,$mediaType，$body"
    }
}
