package com.tencent.bkrepo.nuget.model.v3

/**
 * reference: https://docs.microsoft.com/en-us/nuget/api/registration-base-url-resource#registration-index
 */
data class RegistrationIndex(
    val count: Int,
    val items: List<RegistrationPage>
)
