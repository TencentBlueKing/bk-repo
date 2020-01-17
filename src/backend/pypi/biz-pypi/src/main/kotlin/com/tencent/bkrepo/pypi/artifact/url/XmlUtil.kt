package com.tencent.bkrepo.pypi.artifact.url

import com.tencent.bkrepo.pypi.pojo.xml.*
import com.tencent.bkrepo.repository.pojo.node.NodeInfo

object XmlUtil {

    fun getXmlMethodResponse02(packageName: String, nodeList: List<NodeInfo>): String {
        /*
        nodeList分为两部分，
        -- versionNodeList: 版本节点列表
        -- summaryNodeList: 每个版本对应的summary信息
         */
        val versionNodeList = nodeList.filter { it.folder }
        val summaryNodeList = nodeList.filter { !it.folder }

        // 遍历版本
        val values: MutableList<Value> = ArrayList()
        for (node in versionNodeList) {
            values.add(Value(
                null,
                null,
                Struct(getMembers02(packageName, node)),
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

    fun getMembers02(packageName: String, childNode: NodeInfo): List<Member> {
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
                    childNode.name,
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
                    packageName,
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
                    null,
                    null,
                    null,
                    null
                )
            )
        )
        return members
    }

    // TODO
    // fun mapVersionAndSummary(
    //     versionNodeList: MutableList<NodeInfo>,
    //     summaryNodeList: MutableList<NodeInfo>
    // ): MutableMap<NodeInfo, NodeInfo> {
    //
    // }
}
