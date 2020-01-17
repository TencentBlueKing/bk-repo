package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamImplicit
@XStreamAlias("struct")
class Struct(
    @XStreamImplicit
    val memberList: List<Member>?
)
