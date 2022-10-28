package com.tencent.bkrepo.common.operate.service.annotation

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@MustBeDocumented
annotation class OperateLog(val name: String = "", val isNeedHandle: Boolean = false, val handleMethod: String = "")
