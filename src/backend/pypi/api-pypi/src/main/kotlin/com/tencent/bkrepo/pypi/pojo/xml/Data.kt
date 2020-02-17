package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamImplicit

@XStreamAlias("data")
class Data(
    @XStreamImplicit
    val valueList: List<Value>
)
