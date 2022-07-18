package com.tencent.bkrepo.scanner.pojo.license

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import java.io.Serializable

/**
 * a SPDX license info
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class SpdxLicenseObject(
    val reference: String,
    val isDeprecatedLicenseId: Boolean,
    val detailsUrl: String,
    val referenceNumber: Long,
    val name: String,
    val licenseId: String,
    val seeAlso: MutableList<String>,
    val isOsiApproved: Boolean,
    val isFsfLibre: Boolean?
) : Serializable
