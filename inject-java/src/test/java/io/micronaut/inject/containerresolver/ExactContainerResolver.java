/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.containerresolver;

import io.micronaut.context.beans.definition.BeanDefinitionInjectionPoint;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.BeanDefinitionInjectionPointResolver;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collection;
import java.util.Optional;

public final class ExactContainerResolver implements BeanDefinitionInjectionPointResolver {
    @Override
    public Optional<BeanDefinitionInjectionPoint<ClassElement>> resolve(
        ClassElement beanType, ClassElement requestedType, AnnotationMetadata metadata,
        String parameterName, VisitorContext visitorContext) {
        if (beanType.getName().startsWith("test.exactcontainers.")
            && (requestedType.isAssignable(Optional.class) || requestedType.isAssignable(Collection.class))) {
            return Optional.of(new BeanDefinitionInjectionPoint.BeanInjectionPoint<>(requestedType, metadata));
        }
        return Optional.empty();
    }
}
