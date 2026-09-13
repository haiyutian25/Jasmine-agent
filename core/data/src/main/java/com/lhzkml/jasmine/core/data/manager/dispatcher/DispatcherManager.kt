package com.lhzkml.jasmine.core.data.manager.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Provides injectable coroutine dispatchers so the data layer never references
 * [Dispatchers] directly. ViewModels do not receive this: they stay on
 * `viewModelScope` and all threading concerns live in the data layer.
 *
 * Only dispatchers with real call sites live here — `Dispatchers.Main` was
 * dropped (never used: ViewModels get the main thread via `viewModelScope`).
 */
interface DispatcherManager {
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

/**
 * Default [DispatcherManager] backed by the standard [Dispatchers].
 */
class DispatcherManagerImpl : DispatcherManager {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}
