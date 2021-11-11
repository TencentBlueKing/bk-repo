package com.tencent.bkrepo.oci.controller

import com.tencent.bkrepo.oci.constant.OCI_API_PREFIX
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * oci blob controller
 */
@RestController
@RequestMapping(OCI_API_PREFIX)
class OciBlobController {
    /**
     * 检查blob文件是否存在
     */
    @RequestMapping("/{projectId}/{repoName}/**/blobs/{digest}", method = [RequestMethod.HEAD])
    fun checkBlobExists() {
        TODO()
    }

    /**
     * 上传blob文件或者是完成上传，通过请求头来判断
     */
    @PutMapping("/{projectId}/{repoName}/**/blobs/uploads/{uuid}")
    fun uploadBlob() {
        TODO()
    }

    /**
     * 开始上传blob文件
     */
    @PostMapping("/{projectId}/{repoName}/**/blobs/uploads/")
    fun startBlobUpload() {
        TODO()
    }

    /**
     * 追加上传
     */
    @RequestMapping("/{projectId}/{repoName}/**/blobs/uploads/{uuid}", method = [RequestMethod.PATCH])
    fun appendBlobUpload() {
        TODO()
    }
}
