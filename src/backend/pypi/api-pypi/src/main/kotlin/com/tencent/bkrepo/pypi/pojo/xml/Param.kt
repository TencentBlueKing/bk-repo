package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.annotations.XStreamAlias

@XStreamAlias("param")
class Param(
    val value: Value
)
