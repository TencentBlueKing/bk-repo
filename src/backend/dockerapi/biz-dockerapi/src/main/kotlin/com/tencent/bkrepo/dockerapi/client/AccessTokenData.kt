package com.tencent.bkrepo.dockerapi.client

import com.fasterxml.jackson.annotation.JsonProperty

data class AccessTokenData(
    @JsonProperty("access_token")
    val accessToken: String
)