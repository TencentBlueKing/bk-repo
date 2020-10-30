package com.tencent.bkrepo.rpm.exception

import java.lang.RuntimeException

class RpmConfNotFoundException(error: String) : RuntimeException(error)
