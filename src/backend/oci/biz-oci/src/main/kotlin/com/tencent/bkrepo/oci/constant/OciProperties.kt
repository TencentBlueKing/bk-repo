package com.tencent.bkrepo.oci.constant

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("oci")
data class OciProperties (
	var authUrl: String = "localhost"
)
