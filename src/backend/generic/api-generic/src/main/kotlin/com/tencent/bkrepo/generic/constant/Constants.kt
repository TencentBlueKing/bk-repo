package com.tencent.bkrepo.generic.constant

const val REPO_TYPE = "GENERIC"

const val DEFAULT_MIME_TYPE = "application/octet-stream"

const val CONTENT_DISPOSITION_TEMPLATE = "attachment; filename=\"%s\""

const val BKREPO_PREFIX = "X-BKREPO-"

const val HEADER_OVERWRITE = BKREPO_PREFIX + "OVERWRITE"
const val HEADER_SHA256 = BKREPO_PREFIX + "SHA256"
const val HEADER_EXPIRES = BKREPO_PREFIX + "EXPIRES"
const val HEADER_SIZE = BKREPO_PREFIX + "SIZE"

const val BKREPO_META_PREFIX = "X-BKREPO-META-"
