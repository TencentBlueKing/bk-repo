package com.tencent.bkrepo.composer.exception

import java.lang.RuntimeException

class ComposerPackageMessageDeficiencyException(error: String) : RuntimeException(error)
