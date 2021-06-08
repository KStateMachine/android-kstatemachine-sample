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
import ru.nsk.kstatemachinesample.utils.singleShotTimer
import ru.nsk.kstatemachinesample.utils.tickerFlow

private const val JUMP_DURATION_MS = 2000L
private const val INITIAL_AMMO = 60u
private const val SHOOTING_INTERVAL_MS = 100L

class MainViewModel : ViewModel() {
    private val _controlEvent = MutableLiveData<ControlEvent>()
    val controlEvent: LiveData<ControlEvent> get() = _controlEvent

    private val _currentState = MutableLiveData<HeroState>()
    val currentState: LiveData<HeroState> get() = _currentState

    private val _activeStates = MutableLiveData<List<HeroState>>()
    val activeStates: LiveData<List<HeroState>> get() = _activeStates

    private val _ammoLeft = MutableLiveData(INITIAL_AMMO)
    val ammoLeft: LiveData<UInt> get() = _ammoLeft

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
                    guard = { shooting.ammoLeft > 0u }
                    targetState = shooting
                }
            }
            shooting {
                transition<FireReleaseEvent> { targetState = notShooting }
                transition<OutOfAmmoEvent> { targetState = notShooting }

                onEntry {
                    shootingTimer = viewModelScope.launch {
                        tickerFlow(SHOOTING_INTERVAL_MS).collect {
                            if (ammoLeft == 0u)
                                sendEvent(OutOfAmmoEvent)
                            else
                                (--ammoLeft).also { _ammoLeft.value = it }
                        }
                    }
                }
                onExit { shootingTimer.cancel() }
            }
        }

        onStateChanged {
            _activeStates.value = activeStates().filterIsInstance<HeroState>()
            if (it is HeroState) _currentState.value = it
        }
    }

    fun sendEvent(event: ControlEvent) {
        _controlEvent.value = event
        machine.processEvent(event)
    }
}

sealed class ControlEvent : Event {
    object JumpPressEvent : ControlEvent()
    object JumpCompleteEvent : ControlEvent()
    object DuckPressEvent : ControlEvent()
    object DuckReleaseEvent : ControlEvent()
    object FirePressEvent : ControlEvent()
    object FireReleaseEvent : ControlEvent()
    object OutOfAmmoEvent : ControlEvent()
}

sealed class HeroState(name: String) : DefaultState(name) {
    class Standing : HeroState("Standing")
    class Jumping : HeroState("Jumping")
    class Ducking : HeroState("Ducking")
    class AirAttacking : HeroState("AirAttacking") {
        var isDownPressed = true
    }

    class Shooting : HeroState("Shooting") {
        var ammoLeft = INITIAL_AMMO
        lateinit var shootingTimer: Job
    }

    class NotShooting : HeroState("NotShooting")
}
