package com.tencent.bkrepo.pypi.artifact.url

import com.tencent.bkrepo.pypi.pojo.xml.Data
import com.tencent.bkrepo.pypi.pojo.xml.MethodResponse
import com.tencent.bkrepo.pypi.pojo.xml.Param
import com.tencent.bkrepo.pypi.pojo.xml.Params
import com.tencent.bkrepo.pypi.pojo.xml.Struct
import com.tencent.bkrepo.pypi.pojo.xml.Value
import com.tencent.bkrepo.pypi.pojo.xml.Array
import com.tencent.bkrepo.pypi.pojo.xml.Member
import com.tencent.bkrepo.pypi.pojo.xml.XmlConvertUtil

object XmlUtil {

    fun getXmlMethodResponse(nodeList: List<Map<String, Any>>): String {
        val values: MutableList<Value> = ArrayList()
        // 过滤掉重复节点，每个节点对应一个Struct
        for (node in nodeList) {
            values.add(Value(
                null,
                null,
                Struct(getMembers(node["metadata"] as Map<String, String>)),
                null
            ))
        }

        val methodResponse =
            MethodResponse(
                Params(
                    listOf(
                        Param(
                            Value(
                                null,
                                null,
                                null,
                                Array(
                                    Data(
                                        // 按版本分段
                                        values
                                    )
                                )
                            )
                        )
                    )
                )
            )
        return (XmlConvertUtil.convert(methodResponse))
    }

    fun getMembers(metadata: Map<String, String>): List<Member> {
        val members: MutableList<Member> = ArrayList()
        members.add(
            Member(
                "_pypi_ordering",
                Value(
                    null,
                    0,
                    null,
                    null
                )
            )
        )
        members.add(
            Member(
                "version",
                Value(
                    metadata["version"],
                    null,
                    // 填入子节点name
                    null,
                    null
                )
            )
        )
        members.add(
            Member(
                "name",
                Value(
                    metadata["name"],
                    null,
                    null,
                    null
                )
            )
        )
        members.add(
            Member(
                "summary",
                Value(
                    metadata["summary"],
                    null,
                    null,
                    null
                )
            )
        )
        return members
    }
}
