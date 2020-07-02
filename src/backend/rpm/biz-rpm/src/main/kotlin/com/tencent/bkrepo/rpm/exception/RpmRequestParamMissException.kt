package com.tencent.bkrepo.rpm.exception

import java.lang.RuntimeException

class RpmRequestParamMissException(error: String) : RuntimeException(error)
