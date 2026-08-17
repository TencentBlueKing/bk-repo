package com.tencent.bkrepo.auth.service.tof

import com.tencent.bkrepo.auth.config.TofProperties
import com.tencent.bkrepo.auth.pojo.ApiResponse
import com.tencent.bkrepo.auth.pojo.token.OrgScope
import com.tencent.bkrepo.auth.util.HttpUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TOF 用户组织归属解析")
class TofUserDeptClientTest {

    @BeforeEach
    fun setUp() {
        mockkObject(HttpUtils)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(HttpUtils)
    }

    @Test
    fun `should use typeId string as scopeType for every valid department`() {
        stubTof(
            staffBody = staffJson(USER_ID, GROUP_ID),
            parentBody = deptListJson(
                deptJson(typeId = "6", id = BG_ID, name = "Sample BG"),
                deptJson(typeId = "1", id = DEPT_ID, name = "Sample Dept"),
                deptJson(typeId = "99", id = OTHER_ID, name = "Other Type"),
            ),
            deptBody = deptInfoJson(typeId = "7", id = CENTER_ID, name = "Sample Center"),
        )

        val membership = client().getUserOrgMembership(USER_ID)

        assertEquals(USER_ID, membership.userId)
        assertEquals(
            listOf(
                OrgScope("6", BG_ID, "Sample BG"),
                OrgScope("1", DEPT_ID, "Sample Dept"),
                OrgScope("99", OTHER_ID, "Other Type"),
                OrgScope("7", CENTER_ID, "Sample Center"),
            ),
            membership.scopes,
        )
    }

    @Test
    fun `should skip invalid typeId and empty department id`() {
        stubTof(
            staffBody = staffJson(USER_ID, GROUP_ID),
            parentBody = deptListJson(
                deptJson(typeId = "abc", id = "skip-type", name = "Invalid Type"),
                deptJson(typeId = "1", id = "  ", name = "Blank Id"),
                deptJson(typeId = "1", id = DEPT_ID, name = "Sample Dept"),
            ),
            deptBody = deptInfoJson(typeId = "7", id = CENTER_ID, name = "Sample Center"),
        )

        val membership = client().getUserOrgMembership(USER_ID)

        assertEquals(
            listOf(
                OrgScope("1", DEPT_ID, "Sample Dept"),
                OrgScope("7", CENTER_ID, "Sample Center"),
            ),
            membership.scopes,
        )
    }

    @Test
    fun `should dedupe scopes by type and value`() {
        stubTof(
            staffBody = staffJson(USER_ID, GROUP_ID),
            parentBody = deptListJson(
                deptJson(typeId = "7", id = CENTER_ID, name = "Parent Center"),
            ),
            deptBody = deptInfoJson(typeId = "7", id = CENTER_ID, name = "Self Center"),
        )

        val membership = client().getUserOrgMembership(USER_ID)

        assertEquals(listOf(OrgScope("7", CENTER_ID, "Parent Center")), membership.scopes)
    }

    private fun client(): TofUserDeptClient {
        return TofUserDeptClient(
            TofProperties(host = HOST, appCode = "code", appSecret = "secret"),
        )
    }

    private fun stubTof(staffBody: String, parentBody: String, deptBody: String) {
        every { HttpUtils.doRequest(any(), any(), any()) } answers {
            val url = secondArg<Request>().url.toString()
            val content = when {
                "get_staff_info_by_login_name" in url -> staffBody
                "get_parent_dept_infos" in url -> parentBody
                "get_dept_info" in url -> deptBody
                else -> error("unexpected TOF url [$url]")
            }
            ApiResponse(200, content)
        }
    }

    private fun staffJson(userId: String, groupId: String): String {
        return """{"data":{"LoginName":"$userId","ChineseName":"张三","GroupId":"$groupId","StatusId":"1"}}"""
    }

    private fun deptListJson(vararg depts: String): String {
        return """{"data":[${depts.joinToString(",")}]}"""
    }

    private fun deptInfoJson(typeId: String, id: String, name: String): String {
        return """{"data":${deptJson(typeId, id, name)}}"""
    }

    private fun deptJson(typeId: String, id: String, name: String): String {
        return """{"TypeId":"$typeId","LeaderId":"","Name":"$name","Level":"","Enabled":"1","ParentId":"","ID":"$id"}"""
    }

    companion object {
        private const val HOST = "tof.test"
        private const val USER_ID = "bob"
        private const val GROUP_ID = "100"
        private const val BG_ID = "bg-1"
        private const val DEPT_ID = "dept-1"
        private const val CENTER_ID = "center-1"
        private const val OTHER_ID = "other-1"
    }
}
