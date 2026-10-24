package com.artem.gigatv.data.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteAccessTest {
    private val token = "a".repeat(64)

    @Test fun requiresApprovalAndPersistsOnlyFingerprint() {
        var saved = emptySet<String>()
        val access = RemoteAccess(persist = { saved = it })
        assertFalse(access.authorized(null))
        access.request(token, "192.168.1.2")
        assertFalse(access.authorized(token))
        access.resolve(access.pending.value!!, true)
        assertTrue(access.authorized(token))
        assertFalse(access.authorized("b".repeat(64)))
        assertFalse(saved.contains(token))
        assertTrue(RemoteAccess(initial = saved).authorized(token))
    }

    @Test fun expiredAndRejectedRequestsDoNotGrantAccess() {
        var time = 0L
        val access = RemoteAccess(now = { time })
        access.request(token, "phone")
        time = 60_000
        access.resolve(access.pending.value!!, true)
        assertFalse(access.authorized(token))
        access.request(token, "phone")
        access.resolve(access.pending.value!!, false)
        assertFalse(access.authorized(token))
        access.request("b".repeat(64), "phone")
        assertNull(access.pending.value)
    }

    @Test fun rejectsMalformedTokensAndRevokesStoredAccess() {
        val access = RemoteAccess()
        access.request("short", "phone")
        assertNull(access.pending.value)
        access.request(token, "phone")
        access.resolve(access.pending.value!!, true)
        access.revokeAll()
        assertFalse(access.authorized(token))
    }
}
