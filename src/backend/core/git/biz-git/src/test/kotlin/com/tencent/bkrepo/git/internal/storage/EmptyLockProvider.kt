package com.tencent.bkrepo.git.internal.storage

import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
import java.util.*

class EmptyLockProvider : LockProvider {
    override fun lock(lockConfiguration: LockConfiguration): Optional<SimpleLock> {
        return Optional.of(
            SimpleLock {
                // empty
            }
        )
    }
}
