package com.tencent.bkrepo.npm.utils

import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.npm.constants.NPM_TGZ_TARBALL_PREFIX
import java.lang.StringBuilder

object NpmUtils {
    fun buildPackageTgzTarball(oldTarball: String, tarballPrefix:String, name: String, projectId:String, repoName:String):String{
        val tgzSuffix = name + oldTarball.substringAfter(name)
        val npmPrefixHeader = HeaderUtils.getHeader(NPM_TGZ_TARBALL_PREFIX)
        val newTarball = StringBuilder()
        npmPrefixHeader?.let {
            newTarball.append(it.trimEnd('/')).append('/').append(projectId).append('/')
                .append(repoName).append('/').append(tgzSuffix.trimStart('/'))
        } ?: newTarball.append(tarballPrefix.trimEnd('/')).append('/').append(projectId).append('/')
            .append(repoName).append('/').append(tgzSuffix.trimStart('/'))
        return newTarball.toString()
    }
}