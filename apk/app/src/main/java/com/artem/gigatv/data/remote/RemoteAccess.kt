package com.artem.gigatv.data.remote

import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemotePairing(val fingerprint: String, val host: String, val createdAt: Long)

/** Only hashes are persisted on the TV. A pending request cannot read player state. */
class RemoteAccess(
    initial: Set<String> = emptySet(),
    private val persist: (Set<String>) -> Unit = {},
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private val allowed = LinkedHashSet(initial)
    private val _pending = MutableStateFlow<RemotePairing?>(null)
    val pending = _pending.asStateFlow()
    private var nextRequestAt = Long.MIN_VALUE

    @Synchronized fun authorized(token: String?): Boolean =
        valid(token) && fingerprint(token!!) in allowed

    @Synchronized fun request(token: String?, host: String) {
        if (!valid(token) || authorized(token)) return
        val time = now()
        if (_pending.value?.let { time - it.createdAt < 60_000 } == true || time < nextRequestAt) return
        _pending.value = RemotePairing(fingerprint(token!!), host, time)
        nextRequestAt = time + 30_000
    }

    @Synchronized fun resolve(request: RemotePairing, approve: Boolean) {
        if (_pending.value != request) return
        if (approve && now() - request.createdAt < 60_000) {
            allowed.add(request.fingerprint)
            while (allowed.size > 16) allowed.remove(allowed.first())
            persist(allowed.toSet())
        }
        _pending.value = null
    }

    @Synchronized fun revokeAll() {
        allowed.clear()
        _pending.value = null
        nextRequestAt = now() + 30_000
        persist(emptySet())
    }

    private fun valid(token: String?) = token != null && token.matches(Regex("[a-f0-9]{64}"))
    private fun fingerprint(token: String) = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
