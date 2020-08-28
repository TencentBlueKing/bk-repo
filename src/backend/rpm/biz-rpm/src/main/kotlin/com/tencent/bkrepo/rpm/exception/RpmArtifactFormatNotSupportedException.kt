package com.tencent.bkrepo.rpm.exception

import java.lang.RuntimeException

class RpmArtifactFormatNotSupportedException(error: String) : RuntimeException(error)
