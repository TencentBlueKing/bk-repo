package com.tencent.bkrepo.common.storage.local

import com.tencent.bkrepo.common.storage.core.ClientCredentials

/**
 * 本地文件存储信息
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class LocalStorageCredentials: ClientCredentials {
    var path: String = "/data/upload"
}
