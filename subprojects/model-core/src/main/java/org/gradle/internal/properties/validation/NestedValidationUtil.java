/*
 * Copyright 2023 the original author or authors.
 *
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
 */

package org.gradle.internal.properties.validation;

import com.google.common.reflect.TypeToken;
import org.gradle.api.NonNullApi;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import static org.gradle.internal.reflect.validation.Severity.WARNING;

/**
 * Utility methods for validating {@link org.gradle.api.tasks.Nested} properties.
 */
@NonNullApi
public class NestedValidationUtil  {
    /**
     * Validates that the {@link org.gradle.api.tasks.Nested} annotation
     * supports the given bean type.
     * <p>
     * Only types with annotated properties are supported.
     *
     * @param typeMetadata the type metadata
     * @param validationContext the validation context
     * @param typeToken the type token
     * @param typeName the name of the bean type
     */
    public static void validateBeanType(
        TypeMetadata typeMetadata,
        TypeValidationContext validationContext,
        TypeToken<?> typeToken,
        String typeName
    ) {
        if (!typeMetadata.hasAnnotatedProperties()) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.NESTED_TYPE_UNSUPPORTED)
                    .reportAs(WARNING)
                    .forProperty(typeName)
                    .withDescription(() -> "where nested type '" + typeToken + "' is not supported")
                    .happensBecause("Nested types must declare annotated properties")
                    .addPossibleSolution("Declare annotated properties on the nested type, e.g. 'Provider<T>', 'Iterable<T>', or '<MapProperty<K, V>>', where 'T' and 'V' must have one or more annotated properties")
                    .documentedAt("validation_problems", "nested_type_unsupported")
            );
        }
    }
}
