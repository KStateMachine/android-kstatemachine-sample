package ru.nsk.kstatemachinesample.koin

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import ru.nsk.kstatemachinesample.ui.main.MainViewModel

val koinModule = module {
    viewModel { MainViewModel() }
}
