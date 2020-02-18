package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.annotations.XStreamAlias

@XStreamAlias("array")
class Array(
    val data: Data
)
