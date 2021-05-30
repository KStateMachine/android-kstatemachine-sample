package ru.nsk.kstatemachinesample.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun CoroutineScope.singleShotTimer(timeoutMillis: Long, block: () -> Unit) = launch {
    delay(timeoutMillis)
    block()
}