package ru.nsk.kstatemachinesample.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.*
import ru.nsk.kstatemachine.statemachine.*
import ru.nsk.kstatemachine.transition.onTriggered
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*
import ru.nsk.kstatemachinesample.ui.main.HeroState.*
import ru.nsk.kstatemachinesample.mvi.MviModelHost
import ru.nsk.kstatemachinesample.utils.singleShotTimer
import ru.nsk.kstatemachinesample.utils.tickerFlow

private const val JUMP_DURATION_MS = 1000L
private const val INITIAL_AMMO = 40u
private const val SHOOTING_INTERVAL_MS = 50L

data class ModelData(val ammoLeft: UInt, val activeStates: List<HeroState>)

sealed interface ModelEffect {
    data object AmmoDecremented : ModelEffect
    data class StateEntered(val state: HeroState) : ModelEffect
    data class ControlEventSent(val event: ControlEvent) : ModelEffect
}

class MainViewModel : MviModelHost<ModelData, ModelEffect>, ViewModel() {
    override val model = model(viewModelScope, ModelData(INITIAL_AMMO, listOf(Standing)))

    private val machine = createStateMachineBlocking(viewModelScope, "Hero", ChildMode.PARALLEL) {
        logger = StateMachine.Logger { Log.d(this@MainViewModel::class.simpleName, it()) }

        state("Movement") {
            val airAttacking = addState(AirAttacking())

            addInitialState(Standing) {
                transition<JumpPressEvent>("Jump", targetState = Jumping)
                transition<DuckPressEvent>("Duck", targetState = Ducking)
            }

            addState(Jumping) {
                onEntry {
                    viewModelScope.singleShotTimer(JUMP_DURATION_MS) {
                        sendEvent(JumpCompleteEvent)
                    }
                }
                transition<DuckPressEvent>("AirAttack", targetState = airAttacking)
                transition<JumpCompleteEvent>("Land after jump", targetState = Standing)
            }

            addState(Ducking) {
                transition<DuckReleaseEvent>("StandUp", targetState = Standing)
            }

            airAttacking {
                onEntry { isDuckPressed = true }

                transitionOn<JumpCompleteEvent>("Land after attack") {
                    targetState = { if (this@airAttacking.isDuckPressed) Ducking else Standing }
                }
                transition<DuckPressEvent>("Duck pressed") {
                    onTriggered { this@airAttacking.isDuckPressed = true }
                }
                transition<DuckReleaseEvent>("Duck released") {
                    onTriggered { this@airAttacking.isDuckPressed = false }
                }
            }
        }

        state("Fire") {
            val shooting = addState(Shooting())

            addInitialState(NotShooting) {
                transition<FirePressEvent> {
                    guard = { state.ammoLeft > 0u }
                    targetState = shooting
                }
            }
            shooting {
                transition<FireReleaseEvent>(targetState = NotShooting)
                transition<OutOfAmmoEvent>(targetState = NotShooting)

                onEntry {
                    shootingTimer = viewModelScope.launch {
                        tickerFlow(SHOOTING_INTERVAL_MS).collect {
                            if (state.ammoLeft == 0u)
                                sendEvent(OutOfAmmoEvent)
                            else
                                decrementAmmo()
                        }
                    }
                }
                onExit { shootingTimer.cancel() }
            }
        }

        onTransitionComplete { activeStates, _ ->
            intent {
                state { copy(activeStates = activeStates.filterIsInstance<HeroState>()) }
            }
        }
        onStateEntry { state, _ ->
            intent {
                if (state is HeroState)
                    sendEffect(ModelEffect.StateEntered(state))
            }
        }
    }

    fun sendEvent(event: ControlEvent): Unit = intent {
        sendEffect(ModelEffect.ControlEventSent(event))
        machine.processEvent(event)
    }

    fun reloadAmmo() = intent {
        state { copy(ammoLeft = INITIAL_AMMO) }
    }

    private fun decrementAmmo() = intent {
        state { copy(ammoLeft = ammoLeft - 1u) }
        sendEffect(ModelEffect.AmmoDecremented)
    }
}

sealed interface ControlEvent : Event {
    object JumpPressEvent : ControlEvent
    object JumpCompleteEvent : ControlEvent
    object DuckPressEvent : ControlEvent
    object DuckReleaseEvent : ControlEvent
    object FirePressEvent : ControlEvent
    object FireReleaseEvent : ControlEvent
}

private object OutOfAmmoEvent : ControlEvent

sealed interface HeroState {
    object Standing : DefaultState("Standing"), HeroState
    object Jumping : DefaultState("Jumping"), HeroState
    object Ducking : DefaultState("Ducking"), HeroState
    class AirAttacking : DefaultState("AirAttacking"), HeroState {
        var isDuckPressed = true
    }

    object NotShooting : DefaultState("NotShooting"), HeroState
    class Shooting : DefaultState("Shooting"), HeroState {
        lateinit var shootingTimer: Job
    }
}