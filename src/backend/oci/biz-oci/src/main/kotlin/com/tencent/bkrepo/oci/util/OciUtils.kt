package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.common.service.util.HeaderUtils
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

object OciUtils {

	private val logger = LoggerFactory.getLogger(OciUtils::class.java)

	private val OLD_USER_AGENT_PATTERN = Pattern.compile("^(?:docker/1\\.(?:3|4|5|6|7(?!\\.[0-9]-dev))|Go ).*$")

	fun putHasStream(): Boolean {
		val userAgent = HeaderUtils.getHeader("User-Agent").orEmpty()
		logger.debug("User agent header: [$userAgent]")
		return OLD_USER_AGENT_PATTERN.matcher(userAgent).matches()
	}

	fun buildManifestPath(packageName: String, tag: String): String {
		return "/$packageName/$tag/manifest.json"
	}
}
