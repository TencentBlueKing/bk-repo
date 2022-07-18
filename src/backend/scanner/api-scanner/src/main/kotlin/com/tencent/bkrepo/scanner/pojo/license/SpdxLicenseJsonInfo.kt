package com.tencent.bkrepo.scanner.pojo.license

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import java.io.File
import java.io.Serializable

/**
 * SPDX license json file
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class SpdxLicenseJsonInfo(
    val licenseListVersion: String,
    val licenses: MutableList<SpdxLicenseObject>,
    val releaseDate: String
) : Serializable
