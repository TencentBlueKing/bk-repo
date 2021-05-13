package com.tencent.bkrepo.nuget.model.v3

import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI

data class RegistrationPage(
    /* The URL to the registration page */
    @JsonProperty("@id")
    val id: URI,
    /* The number of registration leaves in the page */
    val count: Int,
    // not required
    val items: List<RegistrationLeaf>,
    val lower: String,
    // not required
    val parent: URI,
    val upper: String
)
