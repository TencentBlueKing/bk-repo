package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.constant.EMPTYSTR
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest


class PathUtil {
    companion object{
        fun prefix(projectId: String, repoName:String): String {
            return  "/v2/$projectId/$repoName/"
        }

        fun artifactName(request:HttpServletRequest,pattern:String, projectId:String,repoName:String) :String {
            var restOfTheUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
            var name = restOfTheUrl.replaceAfterLast(pattern,EMPTYSTR).removeSuffix(pattern).removePrefix(prefix(projectId,repoName))
            return name
        }
    }
}