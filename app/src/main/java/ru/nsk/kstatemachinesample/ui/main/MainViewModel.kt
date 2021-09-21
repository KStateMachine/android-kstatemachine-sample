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

class MainViewModel : ViewModel() {
    private val _controlEventChanged = SingleLiveEvent<ControlEvent>()
    val controlEventChanged: LiveData<ControlEvent> get() = _controlEventChanged

    private val _currentStateChanged = SingleLiveEvent<HeroState>()
    val currentStateChanged: LiveData<HeroState> get() = _currentStateChanged

    private val _activeStates = MutableLiveData<List<HeroState>>()
    val activeStates: LiveData<List<HeroState>> get() = _activeStates

    private val _ammoLeft = MutableLiveData(INITIAL_AMMO)
    val ammoLeft: LiveData<UInt> get() = _ammoLeft

    private val _ammoDecremented = SingleLiveEvent<Unit>()
    val ammoDecremented: LiveData<Unit> get() = _ammoDecremented

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
            val shooting = addState(Shooting(_ammoLeft, _ammoDecremented))

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
                                decrement()
                        }
                    }
                }
                onExit { shootingTimer.cancel() }
            }
        }

        onStateChanged {
            _activeStates.value = activeStates().filterIsInstance<HeroState>()
            if (it is HeroState) _currentStateChanged.value = it
        }
    }

    fun sendEvent(event: ControlEvent) {
        _controlEventChanged.value = event
        machine.processEvent(event)
    }

    fun reloadAmmo() {
        val state = machine.requireState("Fire").requireState(Shooting.NAME) as Shooting
        state.reload()
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

    class Shooting(
        private val ammoLeftLiveData: MutableLiveData<UInt>,
        private val ammoDecremented: SingleLiveEvent<Unit>,
    ) : HeroState(NAME) {
        private var _ammoLeft = INITIAL_AMMO
        val ammoLeft get() = _ammoLeft

        lateinit var shootingTimer: Job

        fun decrement() {
            --_ammoLeft
            ammoLeftLiveData.value = _ammoLeft
            ammoDecremented.call()
        }

        fun reload() {
            _ammoLeft = INITIAL_AMMO
            ammoLeftLiveData.value = _ammoLeft
        }

        companion object {
            const val NAME = "Shooting"
        }
    }

    class NotShooting : HeroState("NotShooting")
}
