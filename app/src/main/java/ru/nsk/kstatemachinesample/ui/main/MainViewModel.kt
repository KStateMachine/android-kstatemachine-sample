package ru.nsk.kstatemachinesample.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.nsk.kstatemachine.*
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*
import ru.nsk.kstatemachinesample.ui.main.NinjaState.*
import ru.nsk.kstatemachinesample.utils.singleShotTimer
import ru.nsk.kstatemachinesample.utils.tickerFlow

private const val JUMP_DURATION_MS = 2000L
private const val INITIAL_AMMO = 30u
private const val SHOOTING_INTERVAL_MS = 100L

class MainViewModel : ViewModel() {
    private val _currentState = MutableLiveData<IState>()
    val currentState: LiveData<IState> get() = _currentState

    private val _ammoLeft = MutableLiveData<UInt>()
    val ammoLeft: LiveData<UInt> get() = _ammoLeft

    private val machine = createStateMachine("Ninja", ChildMode.PARALLEL) {
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
                        machine.processEvent(JumpCompleteEvent)
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
                                machine.processEvent(OutOfAmmoEvent)
                            else
                                (--ammoLeft).also { _ammoLeft.value = it }
                        }
                    }
                }
                onExit { shootingTimer.cancel() }
            }
        }

        onStateChanged { _currentState.value = it }
    }

    fun processEvent(event: ControlEvent) = machine.processEvent(event)
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

sealed class NinjaState(name: String) : DefaultState(name) {
    class Standing : NinjaState("Standing")
    class Jumping : NinjaState("Jumping")
    class Ducking : NinjaState("Ducking")
    class AirAttacking : NinjaState("AirAttacking") {
        var isDownPressed = true
    }

    class Shooting : NinjaState("Shooting") {
        var ammoLeft = INITIAL_AMMO
        lateinit var shootingTimer: Job
    }

    class NotShooting : NinjaState("NotShooting")
}
