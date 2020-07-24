package com.tencent.bkrepo.common.storage.innercos.request

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.PATH_DELIMITER
import com.tencent.bkrepo.common.storage.innercos.client.ClientConfig
import com.tencent.bkrepo.common.storage.innercos.encode
import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.AUTHORIZATION
import com.tencent.bkrepo.common.storage.innercos.http.Headers.Companion.HOST
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.sign.CosSigner
import okhttp3.RequestBody
import java.util.TreeMap

abstract class CosRequest(
    val method: HttpMethod,
    uri: String
) {
    val headers = TreeMap<String, String>()
    val parameters = TreeMap<String, String?>()
    var url: String = StringPool.EMPTY

    private val requestUri: String = StringBuilder().append(PATH_DELIMITER).append(uri.trim(PATH_DELIMITER)).toString()

    open fun buildRequestBody(): RequestBody? = null

    open fun sign(credentials: InnerCosCredentials, config: ClientConfig): String {
        return headers[AUTHORIZATION] ?: run {
            val endpoint = config.endpointBuilder.buildEndpoint(credentials.region, credentials.bucket).apply { headers[HOST] = this }
            val resolvedHost = config.endpointResolver.resolveEndpoint(endpoint)
            url = config.httpProtocol.getScheme() + resolvedHost + requestUri
            if (parameters.isNotEmpty()) {
                url += "?" + getFormatParameters()
            }
            return CosSigner.sign(this, credentials, config.signExpired).apply { headers[AUTHORIZATION] = this }
        }
    }

    fun getFormatMethod(): String = method.name.toLowerCase()

    fun getFormatUri(): String = requestUri

    fun getFormatParameters(): String {
        return parameters.map {
            "${it.key.encode()}=${it.value?.encode().orEmpty()}"
        }.joinToString("&")
    }

    fun getFormatHeaders(): String {
        return headers.map {
            "${it.key.toLowerCase().encode()}=${it.value.encode()}"
        }.joinToString("&")
    }

    companion object {
        @JvmStatic
        protected val xmlMapper = XmlMapper()
    }
}
