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
package io.micronaut.inject.containerresolver

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class ExactContainerResolverSpec extends AbstractTypeElementSpec {
    void 'classpath resolver overrides Optional and collection routing for fields constructors and methods'() {
        given:
        def context = buildContext('test.exactcontainers.Consumer', '''
            package test.exactcontainers;
            import jakarta.inject.*;
            import io.micronaut.context.annotation.*;
            import java.util.*;
            @Singleton
            class Consumer {
                @Inject @Named("exact") Optional<String> fieldOptional;
                @Inject @Named("exact") List<String> fieldList;
                @Inject @Named("exact") Set<String> fieldSet;
                @Value("${answer:property}") Optional<String> property;
                final Optional<String> ctorOptional;
                final List<String> ctorList;
                final Set<String> ctorSet;
                Optional<String> methodOptional;
                List<String> methodList;
                Set<String> methodSet;
                Consumer(@Named("exact") Optional<String> o, @Named("exact") List<String> l, @Named("exact") Set<String> s) {
                    ctorOptional = o; ctorList = l; ctorSet = s;
                }
                @Inject void init(@Named("exact") Optional<String> o, @Named("exact") List<String> l, @Named("exact") Set<String> s) {
                    methodOptional = o; methodList = l; methodSet = s;
                }
            }
            @Factory class Producers {
                @Singleton @Named("exact") Optional<String> optional() { return Optional.of("optional"); }
                @Singleton @Named("exact") List<String> list() { return List.of("list"); }
                @Singleton @Named("exact") Set<String> set() { return Set.of("set"); }
                @Singleton @Named("exact") String element() { return "element"; }
            }
        ''')

        when:
        def bean = getBean(context, 'test.exactcontainers.Consumer')

        then:
        bean.fieldOptional == Optional.of('optional')
        bean.ctorOptional == bean.fieldOptional
        bean.methodOptional == bean.fieldOptional
        bean.fieldList == ['list']
        bean.ctorList == bean.fieldList
        bean.methodList == bean.fieldList
        bean.fieldSet == (['set'] as Set)
        bean.ctorSet == bean.fieldSet
        bean.methodSet == bean.fieldSet
        bean.property == Optional.of('property')

        cleanup:
        context.close()
    }
}
