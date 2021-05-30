package ru.nsk.kstatemachinesample.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import ru.nsk.kstatemachine.*
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*
import ru.nsk.kstatemachinesample.ui.main.NinjaState.*
import ru.nsk.kstatemachinesample.utils.singleShotTimer

private const val JUMP_DURATION_MS = 2000L

class MainViewModel : ViewModel() {
    private val _currentState = MutableLiveData<IState>()
    val currentState: LiveData<IState> get() = _currentState

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
                transition<FirePressEvent> { targetState = shooting }
            }
            shooting {
                transition<FireReleaseEvent> { targetState = notShooting }
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
}

sealed class NinjaState(name: String) : DefaultState(name) {
    class Standing : NinjaState("Standing")
    class Jumping : NinjaState("Jumping")
    class Ducking : NinjaState("Ducking")
    class AirAttacking : NinjaState("AirAttacking") {
        var isDownPressed = true
    }
    class Shooting : NinjaState("Shooting")
    class NotShooting : NinjaState("NotShooting")
}
