package com.tencent.bkrepo.dockeradapter.client;

import com.fasterxml.jackson.annotation.JsonProperty

data class PublicKey(
    @JsonProperty("public_key")
    val publicKey: String
)