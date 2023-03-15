package ru.nsk.kstatemachinesample.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class MviModel<State, Effect>(val scope: CoroutineScope, initialState: State) {
    private val _stateFlow = MutableStateFlow(initialState)
    val stateFlow = _stateFlow.asStateFlow()

    private val _effectChannel = Channel<Effect>()
    val effectFlow = _effectChannel.receiveAsFlow()

    suspend fun sendEffect(effect: Effect) = _effectChannel.send(effect)

    fun state(block: State.() -> State) {
        _stateFlow.value = _stateFlow.value.block()
    }
}

/**
 * Typically a ViewModel implements this interface
 */
interface MviModelHost<State, Effect> {
    val model: MviModel<State, Effect>

    fun <State, Effect> MviModelHost<State, Effect>.model(scope: CoroutineScope, initialState: State) =
        MviModel<State, Effect>(scope, initialState)

    /**
     * This block is used to change model state and emit effects
     */
    fun intent(context: CoroutineContext = EmptyCoroutineContext, block: suspend MviModel<State, Effect>.() -> Unit) {
        model.scope.launch(context) { model.block() }
    }

    val state: State get() = model.stateFlow.value
}

fun <State, Effect> MviModelHost<State, Effect>.observe(
    lifecycleOwner: LifecycleOwner,
    onState: ((State) -> Unit)?,
    onEffect: ((Effect) -> Unit)?,
    lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
) = lifecycleOwner.lifecycleScope.launch {
    lifecycleOwner.lifecycle.repeatOnLifecycle(lifecycleState) {
        onState?.let { launch { model.stateFlow.collect { onState(it) } } }
        onEffect?.let { launch { model.effectFlow.collect { onEffect(it) } } }
    }
}