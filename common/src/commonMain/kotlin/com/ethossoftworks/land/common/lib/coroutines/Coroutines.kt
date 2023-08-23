package com.ethossoftworks.land.common.lib.coroutines

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

// Creates a deferred result that does not cancel the parent upon failure but instead returns an Outcome
fun <T> CoroutineScope.asyncOutcome(
    block: suspend CoroutineScope.() -> T
): Deferred<Outcome<T, Throwable>> = async {
    try {
        Outcome.Ok(block())
    } catch (e: Exception) {
        Outcome.Error(e)
    }
}

// Awaits a deferred and returns an outcome instead of throwing an error on cancellation.
suspend fun <T> Deferred<T>.awaitOutcome(): Outcome<T, Throwable> = try {
    Outcome.Ok(await())
} catch (e: Throwable) {
    Outcome.Error(e)
}

// Awaits a deferred and returns an outcome instead of throwing an error on cancellation
@JvmName("awaitOutcomeWithDeferredOutcome")
suspend fun <T> Deferred<Outcome<T, Throwable>>.awaitOutcome(): Outcome<T, Throwable> = try {
    await()
} catch (e: Throwable) {
    Outcome.Error(e)
}
