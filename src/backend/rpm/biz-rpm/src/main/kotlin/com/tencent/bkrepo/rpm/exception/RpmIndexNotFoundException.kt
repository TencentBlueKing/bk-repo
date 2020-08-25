package com.tencent.bkrepo.rpm.exception

import java.lang.RuntimeException

class RpmIndexNotFoundException(error: String) : RuntimeException(error)
