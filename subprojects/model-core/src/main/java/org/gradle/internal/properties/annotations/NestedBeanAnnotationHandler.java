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
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.JavaReflectionUtil;
import com.google.common.primitives.Primitives;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

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
        TypeToken<?> typeToken = propertyMetadata.getDeclaredType();
        if (Map.class.isAssignableFrom(typeToken.getRawType())) {
            TypeToken<?> keyType = JavaReflectionUtil.extractNestedType((TypeToken<Map<?, ?>>) propertyMetadata.getDeclaredType(), Map.class, 0);
            TypeToken<?> valueType = JavaReflectionUtil.extractNestedType((TypeToken<Map<?, ?>>) propertyMetadata.getDeclaredType(), Map.class, 1);
            validateKeyType(propertyMetadata, validationContext, keyType);
            validateType(propertyMetadata, validationContext, valueType);
        } else {
            validateType(propertyMetadata, validationContext, typeToken);
        }
    }

    private static final Set<Class<?>> UNSUITED_TYPES =
        new HashSet<>(Arrays.asList(String.class, File.class, FileSystemLocation.class));
    private static final Set<Class<?>> SUITED_PARAMETERIZED_TYPES =
        new HashSet<>(Arrays.asList(Provider.class, Iterable.class, List.class, Map.class));

    private static boolean isSuitedType(Class<?> cls) {
        for (Class<?> suitedType : UNSUITED_TYPES) {
            if (suitedType.isAssignableFrom(cls)) {
                return false;
            }
        }
        return !Primitives.isWrapperType(cls);
    }

    private static boolean isSuitedParameterizedType(Class<?> cls) {
        for (Class<?> suitedParameterizedType : SUITED_PARAMETERIZED_TYPES) {
            if (suitedParameterizedType.isAssignableFrom(cls)) {
                return true;
            }
        }
        return false;
    }

    private static void validateType(
        PropertyMetadata propertyMetadata,
        TypeValidationContext validationContext,
        TypeToken<?> typeToken
    ) {
        Type type = typeToken.getType();
        Class<?> rawType = typeToken.getRawType();
        if (type instanceof ParameterizedType) {
            if (!isSuitedParameterizedType(rawType)) {
                reportTypeValidationProblem(propertyMetadata, validationContext, rawType);
            } else {
                for (Type typeArgument : ((ParameterizedType) type).getActualTypeArguments()) {
                    Class<?> rawArgumentType = (Class<?>) typeArgument;
                    if (!isSuitedType(rawArgumentType)) {
                        reportTypeValidationProblem(propertyMetadata, validationContext, rawArgumentType);
                    }
                }
            }
        } else if (!isSuitedType(rawType)) {
            reportTypeValidationProblem(propertyMetadata, validationContext, rawType);
        }
    }

    private static void reportTypeValidationProblem(
        PropertyMetadata propertyMetadata,
        TypeValidationContext validationContext,
        Class<?> type
    ) {
        validationContext.visitPropertyProblem(problem ->
            problem.withId(ValidationProblemId.NESTED_TYPE_UNSUITED)
                .reportAs(WARNING)
                .forProperty(propertyMetadata.getPropertyName())
                .withDescription(() -> "where type of '" + type.getName() + "' is unsuited for nested annotation")
                .happensBecause("Primitive wrapper types and others are unsuited for nested annotation")
                .addPossibleSolution("Change to a suited type, e.g. 'Provider<T>', 'Iterable<T>' or '<MapProperty<K, V>>'")
                .documentedAt("validation_problems", "nested_type_unsuited")
        );
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
}
