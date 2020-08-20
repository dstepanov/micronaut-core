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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.util.KotlinInvocationContextUtils.KotlinCoroutineInvocation;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.ReturnType;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Reactive method invocation helper.
 *
 * @author Denis stepanov
 * @since 2.1.0
 */
@Internal
@Experimental
public class ReactiveMethodInterceptorHelper {

    protected final MethodInvocationContext<Object, Object> context;
    private final ConversionService<?> conversionService = ConversionService.SHARED;

    /**
     * The constructor.
     *
     * @param context MethodInvocationContext
     */
    public ReactiveMethodInterceptorHelper(MethodInvocationContext<Object, Object> context) {
        this.context = context;
    }

    /**
     * The main method to trigger interception.
     *
     * @return intercepted result
     */
    public Object intercept() {
        return intercept(false);
    }

    /**
     * The main method to trigger interception.
     *
     * @param lazy lazy intercept methods should be invoked
     * @return intercepted result
     */
    public Object intercept(boolean lazy) {
        try {
            beforeIntercept();
        } catch (Exception e) {
            Object error = convertError(e);
            if (error != null) {
                return error;
            }
            throw e;
        }
        ReturnType<Object> returnType = context.getReturnType();
        Class<Object> returnTypeClass = returnType.getType();
        if (CompletionStage.class.isAssignableFrom(returnTypeClass)) {
            if (lazy) {
                return lazyInterceptCompletionStage(() -> {
                    Object result = context.proceed();
                    if (result == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return (CompletableFuture<Object>) result;
                });
            }
            Object result = context.proceed();
            if (result == null) {
                return null;
            }
            return interceptCompletionStage(((CompletableFuture) result));
        } else if (KotlinInvocationContextUtils.isKotlinCoroutine(context)) {
            return KotlinInvocationContextUtils.handleKotlinCoroutine(context, invocation -> interceptKotlinCoroutine(invocation, lazy));
        } else if (Publishers.isConvertibleToPublisher(returnTypeClass)) {
            Publisher<Object> publisherResult;
            if (lazy) {
                publisherResult = lazyInterceptPublisher(() -> {
                    Object result = context.proceed();
                    if (result == null) {
                        return Publishers.empty();
                    }
                    return convertToPublisher(result);
                });
            } else {
                Object result = context.proceed();
                if (result == null) {
                    return null;
                }
                publisherResult = interceptPublisher(convertToPublisher(result));
            }
            return convertPublisherToReactiveType(returnType, publisherResult);
        }
        return interceptDefault(context);
    }

    private Object convertPublisherToReactiveType(ReturnType<Object> returnType, Publisher<Object> publisherResult) {
        if (returnType.getType().isInstance(publisherResult)) {
            return publisherResult;
        }
        return conversionService.convert(publisherResult, returnType.asArgument())
                .orElseThrow(() -> new IllegalStateException("Unconvertible Reactive type: " + publisherResult));
    }

    private Publisher<Object> convertToPublisher(Object result) {
        if (result instanceof Publisher) {
            return (Publisher<Object>) result;
        }
        return conversionService
                .convert(result, Publisher.class)
                .orElseThrow(() -> new IllegalStateException("Unconvertible Reactive type: " + result));
    }

    /**
     * Trigger check before interception, method will covert the exception to appropriate propagation.
     */
    protected void beforeIntercept() {
    }

    /**
     * Coverts the exception for appropriate propagation.
     *
     * @param e The exception
     * @return converted return object or null of exception should be rethrown
     */
    protected @Nullable
    Object convertError(Exception e) {
        ReturnType<Object> returnType = context.getReturnType();
        Class<Object> returnTypeClass = returnType.getType();
        if (CompletionStage.class.isAssignableFrom(returnTypeClass)) {
            CompletableFuture<Object> newFuture = new CompletableFuture<>();
            newFuture.completeExceptionally(e);
            return newFuture;
        } else if (KotlinInvocationContextUtils.isKotlinCoroutine(context)) {
            return KotlinInvocationContextUtils.completeExceptionallyKotlinCoroutine(context, e);
        } else if (Publishers.isConvertibleToPublisher(returnTypeClass)) {
            return convertPublisherToReactiveType(returnType, Publishers.just(e));
        }
        return null;
    }

    /**
     * Proceeds with the invocation and convert the result to {@link CompletionStage}.
     *
     * @param interceptor The interceptor to start from (note: will not be included in the execution)
     * @return result of the proceed call
     */
    protected final CompletionStage<Object> proceedAsCompletionStage(Interceptor interceptor) {
        ReturnType<Object> returnType = context.getReturnType();
        Class<Object> returnTypeClass = returnType.getType();
        if (CompletionStage.class.isAssignableFrom(returnTypeClass)) {
            Object result = context.proceed(interceptor);
            return interceptCompletionStage(((CompletableFuture) result));
        } else if (KotlinInvocationContextUtils.isKotlinCoroutine(context)) {
            CompletableFuture<Object> completableFuture = new CompletableFuture<>();
            KotlinInvocationContextUtils.handleKotlinCoroutine(context, helper -> {
                helper.process(interceptor).whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        completableFuture.complete(result);
                    } else {
                        completableFuture.completeExceptionally(throwable);
                    }
                });
            });
            return completableFuture;
        }
        throw new IllegalStateException("Cannot retry: " + returnTypeClass + " as CompletionStage");
    }

    /**
     * Implement to intercept {@link CompletionStage} value of the proceed call.
     *
     * @param result the result of the proceed call
     * @return intercepted result
     */
    protected CompletionStage<Object> interceptCompletionStage(CompletionStage<Object> result) {
        return result;
    }

    /**
     * Implement to intercept {@link CompletionStage} value of the proceed call.
     *
     * @param resultSupplier the supplier of the result of the proceed call
     * @return intercepted result
     */
    protected CompletionStage<Object> lazyInterceptCompletionStage(Supplier<CompletionStage<Object>> resultSupplier) {
        return interceptCompletionStage(resultSupplier.get());
    }

    /**
     * Implement to intercept Kotlin coroutine call. Default implementation makes interception using {@link CompletionStage}.
     *
     * @param invocation the Kotlin coroutine invocation
     */
    protected void interceptKotlinCoroutine(KotlinCoroutineInvocation invocation, boolean lazy) {
        invocation.replaceResult(
                lazy ? lazyInterceptCompletionStage(invocation::process) : interceptCompletionStage(invocation.process())
        );
    }

    /**
     * Implement to intercept {@link Publisher} value of the proceed call.
     *
     * @param resultSupplier the supplier of the result of the proceed call
     * @return intercepted result
     */
    protected Publisher<Object> lazyInterceptPublisher(Supplier<Publisher<Object>> resultSupplier) {
        return interceptPublisher(resultSupplier.get());
    }

    /**
     * Implement to intercept {@link Publisher} value of the proceed call.
     *
     * @param publisher the result of the proceed call
     * @return intercepted publisher
     */
    protected Publisher<Object> interceptPublisher(Publisher<Object> publisher) {
        return publisher;
    }

    /**
     * Implement to intercept default non-reactive call.
     *
     * @param context {@link MethodInvocationContext}
     * @return result of the proceed call
     */
    protected Object interceptDefault(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }

}
