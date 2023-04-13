/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.properties.annotations;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;

import static org.gradle.internal.reflect.validation.Severity.WARNING;

public class NestedBeanAnnotationHandler extends AbstractPropertyAnnotationHandler {
    public NestedBeanAnnotationHandler(Collection<Class<? extends Annotation>> allowedModifiers) {
        super(Nested.class, Kind.OTHER, ImmutableSet.copyOf(allowedModifiers));
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        Class<?> rawType = propertyMetadata.getDeclaredType().getRawType();
        if (Map.class.isAssignableFrom(rawType)) {
            TypeToken<?> keyType = JavaReflectionUtil.extractNestedType((TypeToken<Map<?, ?>>) propertyMetadata.getDeclaredType(), Map.class, 0);
            validateKeyType(propertyMetadata, validationContext, keyType);
        }
    }

    private static void validateKeyType(
        PropertyMetadata propertyMetadata,
        TypeValidationContext validationContext,
        TypeToken<?> typeToken
    ) {
        Class<?> rawType = typeToken.getRawType();
        if (!rawType.equals(String.class)) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.NESTED_MAP_UNSUPPORTED_KEY_TYPE)
                    .reportAs(WARNING)
                    .forProperty(propertyMetadata.getPropertyName())
                    .withDescription(() -> "where key of nested map is of type '" + rawType.getName() + "'")
                    .happensBecause("Key of nested map must be of type 'String'")
                    .addPossibleSolution("Change type of key to 'String'")
                    .documentedAt("validation_problems", "unsupported_key_type_of_nested_map")
            );
        }
    }

    public static void validateType(
        TypeMetadata typeMetadata,
        PropertyMetadata propertyMetadata,
        TypeValidationContext validationContext,
        TypeToken<?> typeToken
    ) {
        if (!typeMetadata.hasAnnotatedProperties()) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.NESTED_TYPE_UNSUITED)
                    .reportAs(WARNING)
                    .forProperty(propertyMetadata.getPropertyName())
                    .withDescription(() -> "where type of '" + typeToken + "' is unsuited for nested annotation")
                    .happensBecause("Primitive wrapper types and others are unsuited for nested annotation")
                    .addPossibleSolution("Change to a suited type, e.g. 'Provider<T>', 'Iterable<T>' or '<MapProperty<K, V>>'")
                    .documentedAt("validation_problems", "nested_type_unsuited")
            );
        }
    }
}
