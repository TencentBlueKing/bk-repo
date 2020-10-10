package com.tencent.bkrepo.dockeradapter.client

import com.fasterxml.jackson.annotation.JsonProperty

data class AccessTokenData(
    @JsonProperty("access_token")
    val accessToken: String
)