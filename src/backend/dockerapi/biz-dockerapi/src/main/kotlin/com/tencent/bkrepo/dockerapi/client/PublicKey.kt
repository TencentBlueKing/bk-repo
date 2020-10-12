package com.tencent.bkrepo.dockerapi.client;

import com.fasterxml.jackson.annotation.JsonProperty

data class PublicKey(
    @JsonProperty("public_key")
    val publicKey: String
)