package com.tencent.bkrepo.generic.pojo.artifactory

data class JfrogFileUploadResponse(
    val repo: String,
    val path: String,
    val created: String,
    val createdBy: String,
    val downloadUri: String,
    val mimeType: String,
    val size: String,
    val uri: String
    //  checksums" : {
    //    "sha1" : "7e240de74fb1ed08fa08d38063f6a6a91462a815",
    //    "md5" : "47bce5c74f589f4867dbd57e9ca9f808"
    //  },
    //  "originalChecksums" : {
    //  },
)