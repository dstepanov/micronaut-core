/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing.test;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Value;

import javax.inject.Named;

/**
 * Testing class.
 */
@Value
@Introspected
@AllArgsConstructor
public class LombokIntrospectedClass {

    private String field1;
    private String field2;
    private String field3;
    @Named("abc")
    private String nameXyz;

}
