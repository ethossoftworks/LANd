package com.ethossoftworks.land.common.lib.coroutines

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.Deferred

suspend fun <T> Deferred<T>.awaitOutcome(): Outcome<T, Throwable> = try {
    Outcome.Ok(await())
} catch (e: Throwable) {
    Outcome.Error(e)
}
