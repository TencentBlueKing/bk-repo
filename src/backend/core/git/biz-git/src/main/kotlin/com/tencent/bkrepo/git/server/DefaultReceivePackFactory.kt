package com.tencent.bkrepo.git.server

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.ReceivePack
import javax.servlet.http.HttpServletRequest

class DefaultReceivePackFactory {

    fun create(req: HttpServletRequest, db: Repository, artifactContext: ArtifactContext): ReceivePack {
        return createFor(req, db, artifactContext.userId)
    }

    private fun createFor(
        req: HttpServletRequest,
        db: Repository,
        user: String
    ): ReceivePack {
        val rp = ReceivePack(db)
        rp.refLogIdent = toPersonIdent(req, user)
        return rp
    }

    private fun toPersonIdent(req: HttpServletRequest, user: String): PersonIdent {
        return PersonIdent(user, user + "@" + req.remoteHost)
    }
}
