package com.tencent.bkrepo.oci.controller

import com.tencent.bkrepo.oci.constant.OCI_API_PREFIX
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * oci manifest controller
 */
@RestController
@RequestMapping(OCI_API_PREFIX)
class OciManifestController {
    /**
     * 检查manifest文件是否存在
     */
    @RequestMapping("/{projectId}/{repoName}/**/manifests/{reference}", method = [RequestMethod.HEAD])
    fun checkManifestsExists() {
        TODO()
    }

    /**
     * 上传manifest文件
     */
    @PutMapping("/{projectId}/{repoName}/**/manifests/{tag}")
    fun uploadManifests() {
        TODO()
    }
}
