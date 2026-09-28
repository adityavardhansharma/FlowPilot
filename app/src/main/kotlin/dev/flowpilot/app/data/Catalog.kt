package dev.flowpilot.app.data

import dev.flowpilot.core.api.*
import dev.flowpilot.core.sync.ResourceCache
import java.util.concurrent.ConcurrentHashMap

class Catalog(private val client: OpenCodeClient) {
    private val projects = ResourceCache(30_000) { client.projects() }
    private val active = ResourceCache(1_000) { client.activeSessions() }
    private val models = ConcurrentHashMap<String, ResourceCache<List<Model>>>()
    private val agents = ConcurrentHashMap<String, ResourceCache<List<Agent>>>()
    private val defaults = ConcurrentHashMap<String, ResourceCache<Model?>>()
    private val commands = ConcurrentHashMap<String, ResourceCache<List<CommandInfo>>>()
    suspend fun projects() = projects.get()
    suspend fun active() = active.get()
    suspend fun models(directory: String? = null) = models.getOrPut(directory.orEmpty()) { ResourceCache(300_000) { client.models(directory) } }.get()
    suspend fun agents(directory: String? = null) = agents.getOrPut(directory.orEmpty()) { ResourceCache(300_000) { client.agents(directory) } }.get()
    suspend fun defaultModel(directory: String? = null) = defaults.getOrPut(directory.orEmpty()) { ResourceCache(300_000) { client.defaultModel(directory) } }.get()
    suspend fun commands(directory: String? = null) = commands.getOrPut(directory.orEmpty()) { ResourceCache(300_000) { client.commands(directory) } }.get()
    fun invalidate() {
        projects.invalidate(); active.invalidate()
        models.values.forEach { it.invalidate() }; agents.values.forEach { it.invalidate() }; defaults.values.forEach { it.invalidate() }
        commands.values.forEach { it.invalidate() }
    }
    fun event(type: String) {
        if (type.startsWith("project.")) projects.invalidate()
        if (type.startsWith("session.execution.")) active.invalidate()
        if (type.startsWith("model.") || type.startsWith("provider.") || type.startsWith("agent.")) {
            models.values.forEach { it.invalidate() }; agents.values.forEach { it.invalidate() }; defaults.values.forEach { it.invalidate() }
        }
    }
}
