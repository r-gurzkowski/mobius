/*
 * -\-\-
 * Mobius
 * --
 * Copyright (c) 2017-2020 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.mobius.coroutines

import com.spotify.mobius.Connectable
import com.spotify.mobius.Connection
import com.spotify.mobius.MobiusLoop
import com.spotify.mobius.functions.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Factory methods for wrapping Mobius core classes in coroutines transformers. */
interface MobiusCoroutines {

    companion object {
        /**
         * Creates a [Connectable] holding a scope to use as a simple effect handler.
         * Each effect will be launched in a new coroutine concurrently.
         *
         * @param <F> the effect type
         * @param <E> the event type
         */
        fun <F : Any, E : Any> effectHandler(
            coroutineContext: CoroutineContext = EmptyCoroutineContext,
            onEffect: suspend (effect: F, eventConsumer: Consumer<E>) -> Unit
        ) = Connectable { eventConsumer ->
            val scope = CoroutineScope(coroutineContext)
            object : Connection<F> {
                override fun accept(value: F) {
                    scope.launch { onEffect.invoke(value, eventConsumer) }
                }

                override fun dispose() {
                    scope.cancel("Effect Handler disposed")
                }
            }
        }

        /**
         * Create a [CoroutinesSubtypeEffectHandlerBuilder] for handling effects based on their type.
         *
         * @param <F> the effect type
         * @param <E> the event type
         */
        fun <F : Any, E : Any> subtypeEffectHandler() = CoroutinesSubtypeEffectHandlerBuilder<F, E>()

        /**
         * Returns a new [MobiusLoop.Builder] with a [DispatcherWorker] using the supplied [CoroutineContext]
         * as effect runner, and the same values as the current one for the other fields.
         */
        fun <M, E, F> MobiusLoop.Builder<M, E, F>.effectRunner(coroutineContext: CoroutineContext) =
            effectRunner { DispatcherWorker(coroutineContext) }

        /**
         * Returns a new [MobiusLoop.Builder] with a [DispatcherWorker] using the supplied [CoroutineContext]
         * as event runner, and the same values as the current one for the other fields.
         */
        fun <M, E, F> MobiusLoop.Builder<M, E, F>.eventRunner(coroutineContext: CoroutineContext) =
            eventRunner { DispatcherWorker(coroutineContext) }
    }
}
