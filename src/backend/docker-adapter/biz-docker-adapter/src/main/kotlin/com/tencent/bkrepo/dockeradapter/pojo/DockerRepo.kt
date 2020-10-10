package com.tencent.bkrepo.dockeradapter.pojo

data class DockerRepo(
    var repo: String? = null,
    var type: String? = null,
    var imageName: String? = null,
    var imagePath: String? = null,
    var createdBy: String? = null,
    var created: String? = null,
    var modified: String? = null,
    var modifiedBy: String? = null,
    var desc: String? = "",
    var tagCount: Long? = null,
    var downloadCount: Long? = null
)
