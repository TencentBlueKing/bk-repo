package com.tencent.bkrepo.migrate.exception

import java.lang.RuntimeException

class DockerTagInvalidException(override val message: String) : RuntimeException(message)
