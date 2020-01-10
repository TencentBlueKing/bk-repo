package com.tencent.bkrepo.pypi.artifact.url

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.tencent.bkrepo.pypi.pojo.xml.XmlArrayRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlDataRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlMemberRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlMethodResponseRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlParamRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlParamsRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlStructRootElement
import com.tencent.bkrepo.pypi.pojo.xml.XmlValueRootElement
import com.tencent.bkrepo.repository.pojo.node.NodeInfo

object XmlUtil {
    fun getXmlMethodResponse(nodeList: List<NodeInfo>): String {
        val xmlMapper = XmlMapper()
        xmlMapper.setDefaultUseWrapper(false)
        //字段为null，自动忽略，不再序列化
        xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
        //设置XML标签名
        xmlMapper.propertyNamingStrategy = PropertyNamingStrategy.LOWER_CASE
        //设置转换模式
        xmlMapper.enable(MapperFeature.USE_STD_BEAN_NAMING)

        val members: MutableList<XmlMemberRootElement> = ArrayList()
        //遍历节点，添加多个版本信息
        for (node in nodeList) {
            members.add(
                XmlMemberRootElement(
                    "_pypi_ordering",
                    XmlValueRootElement(
                        null,
                        null,
                        null,
                        0
                    )
                )
            )
            members.add(
                XmlMemberRootElement(
                    "version",
                    XmlValueRootElement(
                        null,
                        null,
                        node.metadata?.get("version"),
                        null
                    )
                )
            )
            members.add(
                XmlMemberRootElement(
                    "name",
                    XmlValueRootElement(
                        null,
                        null,
                        node.metadata?.get("name"),
                        null
                    )
                )
            )
            members.add(
                XmlMemberRootElement(
                    "summary",
                    XmlValueRootElement(
                        null,
                        null,
                        node.metadata?.get("summary"),
                        null
                    )
                )
            )
        }

        val xmlMethodResponseRootElement =
            XmlMethodResponseRootElement(
                XmlParamsRootElement(
                    listOf(
                        XmlParamRootElement(
                            XmlValueRootElement(
                                null,
                                XmlArrayRootElement(
                                    XmlDataRootElement(
                                        //按版本分段
                                        mutableListOf(XmlValueRootElement(
                                            XmlStructRootElement(
                                                members
                                            ),
                                            null,
                                            null,
                                            null
                                        )

                                        )
                                    )
                                ),
                                null,
                                null
                            )
                        )
                    )
                )
            )
        return (xmlMapper.writeValueAsString(xmlMethodResponseRootElement))

    }
}