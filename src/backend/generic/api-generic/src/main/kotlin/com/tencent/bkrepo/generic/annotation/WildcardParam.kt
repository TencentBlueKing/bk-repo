package com.tencent.bkrepo.generic.annotation

/**
 * 通配符参数注解
 *
 * @author: carrypan
 * @date: 2019-10-08
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class WildcardParam
