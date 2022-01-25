package ru.nsk.kstatemachinesample.ui.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.nsk.kstatemachine.*
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*
import ru.nsk.kstatemachinesample.ui.main.HeroState.*
import ru.nsk.kstatemachinesample.utils.SingleLiveEvent
import ru.nsk.kstatemachinesample.utils.singleShotTimer
import ru.nsk.kstatemachinesample.utils.tickerFlow

private const val JUMP_DURATION_MS = 1000L
private const val INITIAL_AMMO = 40u
private const val SHOOTING_INTERVAL_MS = 50L

data class ModelData(val ammoLeft: UInt, val activeStates: List<HeroState>)

sealed interface ModelEffect {
    object AmmoDecremented : ModelEffect
    class CurrentStateChanged(val state: HeroState) : ModelEffect
    class ControlEventChanged(val event: ControlEvent) : ModelEffect
}

class MainViewModel : ViewModel() {
    private var data = ModelData(INITIAL_AMMO, emptyList())
        set(value) {
            field = value
            _modelData.value = value
        }
    private val _modelData = MutableLiveData(data)
    val modelData: LiveData<ModelData> get() = _modelData


    private val _modelEffect = SingleLiveEvent<ModelEffect>()
    val modelEffect: LiveData<ModelEffect> get() = _modelEffect

    private val machine = createStateMachine("Hero", ChildMode.PARALLEL) {
        logger = StateMachine.Logger { Log.d(this@MainViewModel::class.simpleName, it) }

        state("Movement") {
            val standing = addInitialState(Standing())
            val jumping = addState(Jumping())
            val ducking = addState(Ducking())
            val airAttacking = addState(AirAttacking())

            standing {
                transition<JumpPressEvent>("Jump") { targetState = jumping }
                transition<DuckPressEvent>("Duck") { targetState = ducking }
            }

            jumping {
                onEntry {
                    viewModelScope.singleShotTimer(JUMP_DURATION_MS) {
                        sendEvent(JumpCompleteEvent)
                    }
                }
                transition<DuckPressEvent>("AirAttack") { targetState = airAttacking }
                transition<JumpCompleteEvent>("Land after jump") { targetState = standing }
            }

            ducking {
                transition<DuckReleaseEvent>("StandUp") { targetState = standing }
            }

            airAttacking {
                onEntry { isDownPressed = true }

                transitionOn<JumpCompleteEvent>("Land after attack") {
                    targetState = { if (this@airAttacking.isDownPressed) ducking else standing }
                }
                transition<DuckPressEvent>("Duck pressed") {
                    onTriggered { this@airAttacking.isDownPressed = true }
                }
                transition<DuckReleaseEvent>("Duck released") {
                    onTriggered { this@airAttacking.isDownPressed = false }
                }
            }
        }

        state("Fire") {
            val notShooting = addInitialState(NotShooting())
            val shooting = addState(Shooting())

            notShooting {
                transition<FirePressEvent> {
                    guard = { data.ammoLeft > 0u }
                    targetState = shooting
                }
            }
            shooting {
                transition<FireReleaseEvent> { targetState = notShooting }
                transition<OutOfAmmoEvent> { targetState = notShooting }

                onEntry {
                    shootingTimer = viewModelScope.launch {
                        tickerFlow(SHOOTING_INTERVAL_MS).collect {
                            if (data.ammoLeft == 0u)
                                sendEvent(OutOfAmmoEvent)
                            else
                                decrementAmmo()
                        }
                    }
                }
                onExit { shootingTimer.cancel() }
            }
        }

        onStateChanged {
            data = data.copy(activeStates = activeStates().filterIsInstance<HeroState>())
            if (it is HeroState)
                _modelEffect.value = ModelEffect.CurrentStateChanged(it)
        }
    }

    fun sendEvent(event: ControlEvent) {
        _modelEffect.value = ModelEffect.ControlEventChanged(event)
        machine.processEvent(event)
    }

    fun reloadAmmo() {
        data = data.copy(ammoLeft = INITIAL_AMMO)
    }

    private fun decrementAmmo() {
        data = data.copy(ammoLeft = data.ammoLeft - 1u)
        _modelEffect.value = ModelEffect.AmmoDecremented
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

sealed class HeroState : DefaultState() {
    class Standing : HeroState()
    class Jumping : HeroState()
    class Ducking : HeroState()
    class AirAttacking : HeroState() {
        var isDownPressed = true
    }
    class NotShooting : HeroState()
    class Shooting : HeroState() {
        lateinit var shootingTimer: Job
    }
}
