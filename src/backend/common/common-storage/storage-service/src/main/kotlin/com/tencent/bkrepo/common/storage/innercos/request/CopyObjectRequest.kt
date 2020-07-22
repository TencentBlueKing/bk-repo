package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.COS_COPY_SOURCE
import com.tencent.bkrepo.common.storage.innercos.PATH_DELIMITER
import com.tencent.bkrepo.common.storage.innercos.client.ClientConfig
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import okhttp3.RequestBody

data class CopyObjectRequest(
    val sourceBucket: String,
    val sourceKey: String,
    val destinationKey: String
) : CosRequest(HttpMethod.PUT, destinationKey) {

    override fun buildRequestBody(): RequestBody {
        return StringPool.EMPTY.toRequestBody()
    }

    override fun sign(credentials: InnerCosCredentials, config: ClientConfig): String {
        val sourceEndpoint = config.endpointBuilder.buildEndpoint(credentials.region, sourceBucket)
        val sourceUri = StringBuilder().append(PATH_DELIMITER).append(sourceKey.trim(PATH_DELIMITER)).toString()
        headers[COS_COPY_SOURCE] = sourceEndpoint + sourceUri
        return super.sign(credentials, config)
    }

}
