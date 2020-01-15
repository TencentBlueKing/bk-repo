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
import java.util.regex.Pattern

object XmlUtil {
    fun getXmlMethodResponse(packageName: String, nodeList: List<NodeInfo>): String {
        val xmlMapper = XmlMapper()
        xmlMapper.setDefaultUseWrapper(false)
        //字段为null，自动忽略，不再序列化
        xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
        //设置XML标签名
        xmlMapper.propertyNamingStrategy = PropertyNamingStrategy.LOWER_CASE
        //设置转换模式
        xmlMapper.enable(MapperFeature.USE_STD_BEAN_NAMING)


        /**
        nodeList分为两部分，
        -- versionNodeList: 版本节点列表
        -- summaryNodeList: 每个版本对应的summary信息
         */
        val versionNodeList = nodeList.filter { it.folder }
        val summaryNodeList = nodeList.filter { !it.folder }

        //遍历版本
        val values: MutableList<XmlValueRootElement> = ArrayList()
        for (node in versionNodeList) {
            values.add(XmlValueRootElement(
                XmlStructRootElement(getMembers(packageName, node)),
                null,
                null,
                null
            ))
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
                                        values
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

    fun getMembers(packageName: String, childNode: NodeInfo): MutableList<XmlMemberRootElement> {
        val members: MutableList<XmlMemberRootElement> = ArrayList()
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
                    //填入子节点name
                    childNode.name,
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
                    packageName,
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
                    null,
                    null
                )
            )
        )
        return members
    }

    //TODO
    // fun mapVersionAndSummary(
    //     versionNodeList: MutableList<NodeInfo>,
    //     summaryNodeList: MutableList<NodeInfo>
    // ): MutableMap<NodeInfo, NodeInfo> {
    //
    // }

    //TODO
    fun getAction(xml: String): String? {
        val actionPattern = "<methodName>(.+)</methodName>"
        val matcher = Pattern.compile(actionPattern).matcher(xml)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    //TODO
    fun getPackageName(xml: String): String {
        val actionPattern = "<string>(.+)</string>"
        val matcher = Pattern.compile(actionPattern).matcher(xml)
        if (matcher.find()) {
            return matcher.group(1)
        }
        throw IllegalArgumentException("")
    }

}