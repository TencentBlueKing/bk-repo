package com.tencent.bkrepo.common.storage.innercos


/**
 * inner cos 身份认证信息
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class InnerCosCredentials {
    var appId: String? = null
    var secretId: String? = null
    var secretKey: String? = null
    var host: String? = null
    var regionName: String? = null
    var bucketName = ""
}