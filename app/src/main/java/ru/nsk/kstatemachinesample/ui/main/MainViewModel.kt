package ru.nsk.kstatemachinesample.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ru.nsk.kstatemachine.*

class MainViewModel : ViewModel() {
    private val _currentState = MutableLiveData<State>()
    val currentState: LiveData<State> get() = _currentState

    private val machine: StateMachine = createStateMachine("Ninja") {
        addInitialState(PlayerState.Standing())
    }
    
    sealed class PlayerState(name: String) : DefaultState(name) {
        class Standing : PlayerState("Standing")
    }
}