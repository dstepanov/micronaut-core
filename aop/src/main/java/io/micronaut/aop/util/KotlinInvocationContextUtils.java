/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop.util;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.KotlinUtils;
import kotlin.coroutines.Continuation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * <p>Internal Utility methods for working with Kotlin <code>suspend</code> functions with {@link io.micronaut.aop.MethodInterceptor}</p>.
 *
 * @author Denis stepanov
 * @since 2.1.0
 */
@Internal
@Experimental
public class KotlinInvocationContextUtils {

    /**
     * Checks if the method invocation is a Kotlin coroutine.
     *
     * @param context {@link MethodInvocationContext}
     * @return true if Kotlin coroutine
     */
    public static boolean isKotlinCoroutine(MethodInvocationContext<Object, Object> context) {
        if (!KotlinUtils.KOTLIN_COROUTINES_SUPPORTED || !context.getExecutableMethod().isSuspend()) {
            return false;
        }
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 0) {
            return false;
        }
        Object lastArgumentValue = parameterValues[parameterValues.length - 1];
        if (lastArgumentValue instanceof Continuation) {
            return true;
        }
        return false;
    }

    /**
     * Handle Kotlin coroutine invocation represented as {@link CompletableFuture}s.
     *
     * @param context  {@link MethodInvocationContext}
     * @param consumer helper
     * @return invocation return value
     */
    public static Object handleKotlinCoroutine(MethodInvocationContext<Object, Object> context, Consumer<KotlinCoroutineInvocation> consumer) {
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 0) {
            throw new IllegalStateException("Expected at least one parameter");
        }
        int lastParameterIndex = parameterValues.length - 1;
        Object lastArgumentValue = parameterValues[lastParameterIndex];
        if (lastArgumentValue instanceof Continuation) {
            Continuation continuation = (Continuation) lastArgumentValue;
            CompletableFutureContinuation completableFutureContinuation;
            if (continuation instanceof CompletableFutureContinuation) {
                completableFutureContinuation = (CompletableFutureContinuation) continuation;
            } else {
                completableFutureContinuation = new CompletableFutureContinuation(continuation);
                parameterValues[lastParameterIndex] = completableFutureContinuation;
            }
            CompletableFuture<Object> completableFuture = completableFutureContinuation.getCompletableFuture();
            consumer.accept(new KotlinCoroutineInvocation() {
                @Override
                public CompletionStage<Object> process(Interceptor interceptor) {
                    Object result = context.proceed(interceptor);
                    if (result != KotlinUtils.COROUTINE_SUSPENDED) {
                        throw new IllegalStateException("Not a Kotlin coroutine");
                    }
                    return completableFuture;
                }

                @Override
                public CompletionStage<Object> process() {
                    Object result = context.proceed();
                    if (result != KotlinUtils.COROUTINE_SUSPENDED) {
                        throw new IllegalStateException("Not a Kotlin coroutine");
                    }
                    return completableFuture;
                }

                @Override
                public void replaceResult(CompletionStage<Object> newResult) {
                    newResult.whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            completableFutureContinuation.continuationComplete(result);
                        } else {
                            completableFutureContinuation.continuationCompleteExceptionally((Throwable) throwable);
                        }
                    });
                }
            });
            return KotlinUtils.COROUTINE_SUSPENDED;
        }
        throw new IllegalStateException("Expected Kotlin Continuation got: " + lastArgumentValue);
    }

    public static Object completeExceptionallyKotlinCoroutine(MethodInvocationContext<Object, Object> context, Throwable throwable) {
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 0) {
            throw new IllegalStateException("Expected at least one parameter");
        }
        int lastParameterIndex = parameterValues.length - 1;
        Object lastArgumentValue = parameterValues[lastParameterIndex];
        if (lastArgumentValue instanceof Continuation) {
            Continuation<Object> continuation = (Continuation) lastArgumentValue;
            CompletableFutureContinuation.Companion.completeExceptionally(continuation, throwable);
            return KotlinUtils.COROUTINE_SUSPENDED;
        }
        throw new IllegalStateException("Expected Kotlin Continuation got: " + lastArgumentValue);
    }

    /**
     * Kotlin coroutine invocation helper.
     */
    public interface KotlinCoroutineInvocation {

        /**
         * Proceeds with the invocation ({@link io.micronaut.aop.InvocationContext}). Returns {@link CompletionStage} representing a coroutine call.
         *
         * @return The {@link CompletionStage}
         */
        CompletionStage<Object> process();

        /**
         * Proceeds with the invocation using the given interceptor as a position to start ({@link io.micronaut.aop.InvocationContext}). Returns {@link CompletionStage} representing a coroutine call.
         *
         * @param from The interceptor to start from (note: will not be included in the execution)
         * @return The {@link CompletionStage}
         */
        CompletionStage<Object> process(Interceptor from);

        /**
         * Replaces the coroutine result.
         *
         * @param newResult new result represented by {@link CompletableFuture}
         */
        void replaceResult(CompletionStage<Object> newResult);

    }

}
