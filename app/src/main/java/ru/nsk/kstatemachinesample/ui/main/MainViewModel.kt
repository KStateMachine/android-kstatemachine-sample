package ru.nsk.kstatemachinesample.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ru.nsk.kstatemachine.*
import ru.nsk.kstatemachinesample.ui.main.ControlEvent.*
import ru.nsk.kstatemachinesample.ui.main.NinjaState.*

class MainViewModel : ViewModel() {
    private val _currentState = MutableLiveData<IState>()
    val currentState: LiveData<IState> get() = _currentState

    private val machine = createStateMachine("Ninja") {
        val standing = addInitialState(Standing())
        val jumping = addState(Jumping())
        val ducking = addState(Ducking())
        val airAttacking = addState(AirAttacking())

        standing {
            transition<JumpPressEvent>("Jump") { targetState = jumping }
            transition<DownPressEvent>("Duck") { targetState = ducking }
        }

        jumping {
            transition<DownPressEvent>("AirAttack") { targetState = airAttacking }
            transition<JumpCompleteEvent>("Land") { targetState = standing }
        }

        ducking {
            transition<DownReleaseEvent>("StandUp") { targetState = standing }
        }

        airAttacking {
            transitionOn<JumpCompleteEvent>("Land") {
                targetState = { if (this@airAttacking.isDownPressed) ducking else standing }
            }
            transition<DownPressEvent>("Down pressed") {
                onTriggered { this@airAttacking.isDownPressed = true }
            }
            transition<DownReleaseEvent>("Down released") {
                onTriggered { this@airAttacking.isDownPressed = false }
            }
        }

        onStateChanged { _currentState.value = it }
    }

    fun processEvent(event: ControlEvent) = machine.processEvent(event)
}

sealed class ControlEvent : Event {
    object JumpPressEvent : ControlEvent()
    object JumpCompleteEvent : ControlEvent()
    object DownPressEvent : ControlEvent()
    object DownReleaseEvent : ControlEvent()
}

sealed class NinjaState(name: String) : DefaultState(name) {
    class Standing : NinjaState("Standing")
    class Jumping : NinjaState("Jumping")
    class Ducking : NinjaState("Ducking")
    class AirAttacking : NinjaState("AirAttacking") {
        /**
         * As alternative to storing and updating this flag we could have a top-level function `isDownPressed()`
         * returning a current button status.
         */
        var isDownPressed = true
    }
}
