package dev.flowpilot.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single-flight reads with a monotonic freshness window. Failures never overwrite the last value. */
class ResourceCache<T>(private val ttlMillis: Long, private val load: suspend () -> T) {
    private val mutex = Mutex()
    private var value: T? = null
    private var populated = false
    private var loadedAt = 0L
    @Volatile private var generation = 0L
    private var loadedGeneration = -1L
    @Synchronized fun invalidate() { generation++ }
    suspend fun get(): T = mutex.withLock {
        val now = System.nanoTime()
        if (populated && loadedGeneration == generation && (now - loadedAt) / 1_000_000 < ttlMillis) {
            @Suppress("UNCHECKED_CAST")
            return@withLock value as T
        }
        val epoch = generation
        val next = load()
        value = next
        populated = true
        loadedAt = System.nanoTime()
        loadedGeneration = epoch
        next
    }
}
