package com.tencent.bkrepo.common.storage.innercos

import com.tencent.bkrepo.common.storage.core.ClientCredentials

/**
 * inner cos 身份认证信息
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class InnerCosCredentials: ClientCredentials{
    var secretId: String? = null
    var secretKey: String? = null
    var region: String? = null
    var bucket = ""
}
