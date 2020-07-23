package com.tencent.bkrepo.pypi.artifact.xml

object XmlUtil {

    fun getSearchArgs(xmlString: String): HashMap<String, String?> {
        val methodCall = XmlConvertUtil.xml2MethodCall(xmlString)
        val action = methodCall.methodName
        val packageName = methodCall.params.paramList[0].value.struct?.memberList?.get(0)?.value?.array?.data?.valueList?.get(0)?.string
        val summary = methodCall.params.paramList[0].value.struct?.memberList?.get(1)?.value?.array?.data?.valueList?.get(0)?.string
        val operation = methodCall.params.paramList[1].value.string
        return hashMapOf("action" to action, "packageName" to packageName, "summary" to summary, "operation" to operation)
    }

    /**
     * null response
     */
    fun getEmptyMethodResponse(): MethodResponse {
        return MethodResponse(
            Params(
                listOf(
                    Param(
                        Value(
                            null,
                            null,
                            null,
                            Array(
                                Data(
                                    mutableListOf()
                                )
                            ),
                            null
                        )
                    )
                )
            )
        )
    }

    fun nodeLis2Values(nodeList: List<Map<String, Any>>): MutableList<Value> {
        val values: MutableList<Value> = ArrayList()
        // 过滤掉重复节点，每个节点对应一个Struct
        for (node in nodeList) {
            values.add(
                Value(
                    null,
                    null,
                    Struct(getMembers(node["metadata"] as Map<String, String>)),
                    null,
                    null
                )
            )
        }
        return values
    }

    fun getXmlMethodResponse(nodeList: List<Map<String, Any>>): String {
        val values: MutableList<Value> = ArrayList()
        // 过滤掉重复节点，每个节点对应一个Struct
        for (node in nodeList) {
            values.add(
                Value(
                    null,
                    null,
                    Struct(getMembers(node["metadata"] as Map<String, String>)),
                    null,
                    null
                )
            )
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
                                ),
                                null
                            )
                        )
                    )
                )
            )
        return (XmlConvertUtil.methodResponse2Xml(methodResponse))
    }

    private fun getMembers(metadata: Map<String, String>): List<Member> {
        val members: MutableList<Member> = ArrayList()
        members.add(
            Member(
                "_pypi_ordering",
                Value(
                    null,
                    0,
                    null,
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
                    null,
                    null
                )
            )
        )
        return members
    }
}
