package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamImplicit
@XStreamAlias("params")
class Params(
    @XStreamImplicit
    val paramList: List<Param>
)
