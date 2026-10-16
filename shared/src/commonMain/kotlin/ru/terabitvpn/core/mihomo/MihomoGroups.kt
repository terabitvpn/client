package ru.terabitvpn.core.mihomo

import ru.terabitvpn.core.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Proxy groups of the running mihomo core for the UI: what each selector points at, the members'
 * delays, picking a member and testing a group. Empty while Xray runs or the tunnel is down.
 */
class MihomoGroups(private val backend: DualCoreBackend?, private val scope: CoroutineScope, private val pingUrl: () -> String) {

    private val _groups = MutableStateFlow<List<ProxyGroupInfo>>(emptyList())
    val groups: StateFlow<List<ProxyGroupInfo>> = _groups.asStateFlow()

    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    private var poll: Job? = null

    init {
        if (backend != null) scope.launch {
            backend.state.collect { s ->
                if (s is VpnState.Connected && backend.onMihomo) start() else stop()
            }
        }
    }

    private fun api(): MihomoApi? = backend?.takeIf { it.onMihomo }?.mihomo?.api

    private fun start() {
        if (poll?.isActive == true) return
        poll = scope.launch(Dispatchers.Default) {
            while (isActive) {
                refresh()
                delay(5_000)
            }
        }
    }

    private fun stop() {
        poll?.cancel()
        poll = null
        _groups.value = emptyList()
    }

    suspend fun refresh() {
        val api = api() ?: return
        _groups.value = runCatching { api.groups() }.getOrDefault(_groups.value)
    }

    fun select(group: String, member: String) {
        scope.launch(Dispatchers.Default) {
            val api = api() ?: return@launch
            // Optimistic: the row moves at once, the next refresh confirms.
            _groups.update { list -> list.map { if (it.name == group) it.copy(now = member) else it } }
            if (api.select(group, member)) api.closeConnections()
            refresh()
        }
    }

    fun test(group: String) {
        if (group in _testing.value) return
        scope.launch(Dispatchers.Default) {
            val api = api() ?: return@launch
            _testing.update { it + group }
            try {
                api.testGroup(group, pingUrl())
                refresh()
            } finally {
                _testing.update { it - group }
            }
        }
    }
}
