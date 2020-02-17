package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.annotations.XStreamAlias

@XStreamAlias("value")
class Value(
    val string: String?,
    val int: Int?,
    val struct: Struct?,
    val array: Array?
)
