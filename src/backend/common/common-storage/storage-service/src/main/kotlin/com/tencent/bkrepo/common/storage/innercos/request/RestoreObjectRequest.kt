package com.tencent.bkrepo.common.storage.innercos.request

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.tencent.bkrepo.common.storage.innercos.PARAMETER_RESTORE
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import okhttp3.RequestBody

data class RestoreObjectRequest(
    val key: String,
    val days: Int,
    val tier: String,
) : CosRequest(HttpMethod.POST, key) {

    init {
        parameters[PARAMETER_RESTORE] = null
    }

    override fun buildRequestBody(): RequestBody {
        return xmlMapper.writeValueAsString(RestoreRequest(days, CasJobParameters(tier))).toRequestBody()
    }

    private data class RestoreRequest(
        @get:JacksonXmlProperty(localName = "Days")
        val days: Int,
        @get:JacksonXmlProperty(localName = "CASJobParameters")
        val casJobParameters: CasJobParameters,
    )

    private data class CasJobParameters(
        @get:JacksonXmlProperty(localName = "Tier")
        val tier: String,
    )
}
