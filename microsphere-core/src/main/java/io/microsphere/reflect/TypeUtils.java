/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microsphere.reflect;

import io.microsphere.util.ClassUtils;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.microsphere.collection.ListUtils.newLinkedList;
import static io.microsphere.lang.function.Predicates.and;
import static io.microsphere.lang.function.Streams.filterAll;
import static io.microsphere.lang.function.Streams.filterList;
import static io.microsphere.util.ArrayUtils.of;
import static io.microsphere.util.ClassUtils.getAllSuperClasses;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;

/**
 * The utilities class for {@link Type}
 *
 * @since 1.0.0
 */
public abstract class TypeUtils {

    public static final Predicate<Type> NON_OBJECT_TYPE_FILTER = t -> t != null && !isObjectType(t);

    public static final Predicate<Class<?>> NON_OBJECT_CLASS_FILTER = (Predicate) NON_OBJECT_TYPE_FILTER;

    public static final Predicate<Type> TYPE_VARIABLE_FILTER = TypeUtils::isTypeVariable;

    public static final Predicate<Type> PARAMETERIZED_TYPE_FILTER = TypeUtils::isParameterizedType;

    public static final Predicate<Type> WILDCARD_TYPE_FILTER = TypeUtils::isWildcardType;

    public static final Predicate<Type> GENERIC_ARRAY_TYPE_FILTER = TypeUtils::isGenericArrayType;

    public static boolean isClass(Type type) {
        return type instanceof Class;
    }

    public static boolean isObjectType(Type type) {
        return Object.class.equals(type);
    }

    public static boolean isParameterizedType(Type type) {
        return type instanceof ParameterizedType;
    }

    public static boolean isTypeVariable(Type type) {
        return type instanceof TypeVariable;
    }

    public static boolean isWildcardType(Type type) {
        return type instanceof WildcardType;
    }

    public static boolean isGenericArrayType(Type type) {
        return type instanceof GenericArrayType;
    }

    public static Type getRawType(Type type) {
        if (isParameterizedType(type)) {
            return ((ParameterizedType) type).getRawType();
        } else {
            return type;
        }
    }

    public static Class<?> getRawClass(Type type) {
        Type rawType = getRawType(type);
        if (isClass(rawType)) {
            return (Class) rawType;
        }
        return null;
    }

    /**
     * the semantics is same as {@link Class#isAssignableFrom(Class)}
     *
     * @param superType  the super type
     * @param targetType the target type
     * @return see {@link Class#isAssignableFrom(Class)}
     */
    public static boolean isAssignableFrom(Type superType, Type targetType) {
        Class<?> superClass = asClass(superType);
        Class<?> targetClass = asClass(targetType);
        return ClassUtils.isAssignableFrom(superClass, targetClass);
    }

    /**
     * the semantics is same as {@link Class#isAssignableFrom(Class)}
     *
     * @param superType  the super type
     * @param targetType the target type
     * @return see {@link Class#isAssignableFrom(Class)}
     */
    protected static boolean isAssignableFrom(Class<?> superType, Class<?> targetType) {
        return ClassUtils.isAssignableFrom(superType, targetType);
    }

    public static Type findActualTypeArgument(Type type, Class<?> interfaceClass, int index) {
        return findActualTypeArguments(type, interfaceClass).get(index);
    }

//    public static List<Type> findActualTypeArguments(Type type, Class<?> interfaceClass) {
//        List<Type> actualTypeArguments = new LinkedList<>();
//        getAllGenericTypes(type, t -> isAssignableFrom(interfaceClass, getRawClass(t)))
//                .forEach(parameterizedType -> {
//                    Type[] typeArguments = parameterizedType.getActualTypeArguments();
//                    actualTypeArguments.addAll(asList(typeArguments));
//                    Class<?> rawClass = getRawClass(parameterizedType);
//                    Type genericSuperclass = rawClass.getGenericSuperclass();
//                    if (genericSuperclass != null) {
//                        actualTypeArguments.addAll(findActualTypeArguments(genericSuperclass, interfaceClass));
//                    }
//                });
//
//        return unmodifiableList(actualTypeArguments);
//    }

    /**
     * For example,
     * <code>type</code> == StringToIntegerConverter.class
     * <code>baseType</code> == Converter.class
     *
     * <prev>
     * type
     * - Generic Super Type = null
     * - Generic Super Interfaces
     * - [0] = StringConverter<String> -> StringConverter<T>
     * Converter<String,T> -> Converter<S,T>
     * </prev>
     *
     * @param type
     * @param baseType
     * @return
     */
    public static List<Type> findActualTypeArguments(Type type, Type baseType) {
        Class targetClass = asClass(type);
        Class baseClass = asClass(baseType);
        if (!isAssignableFrom(baseClass, targetClass)) {
            return emptyList();
        }

        TypeVariable<Class>[] baseTypeParameters = baseClass.getTypeParameters();
        int baseTypeParametersLength = baseTypeParameters.length;
        if (baseTypeParametersLength == 0) { // No type-parameter Class
            return emptyList();
        }


        List<Type> actualTypeArguments = new ArrayList<>(baseTypeParametersLength);

        List<Type> hierarchicalTypes = findAllHierarchicalTypes(type, t -> isAssignableFrom(baseClass, t));
        int hierarchicalTypesSize = hierarchicalTypes.size();

        if (hierarchicalTypesSize < 1) { // No hierarchical type was derived by baseType
            return emptyList();
        }

        TOP:
        for (int i = 0; i < baseTypeParametersLength; i++) {
            TypeVariable<Class> baseTypeParameter = baseTypeParameters[i];
            Class<?> declaredClass = baseTypeParameter.getGenericDeclaration();
            MID:
            for (int j = 0; j < hierarchicalTypesSize; j++) {
                Type hierarchicalType = hierarchicalTypes.get(j);
                ParameterizedType parameterizedType = asParameterizedType(hierarchicalType);
                if (parameterizedType != null) {
                    Class hierarchicalClass = getRawClass(parameterizedType);
                    if (declaredClass.equals(hierarchicalClass)) {
                        Type[] typeArguments = parameterizedType.getActualTypeArguments();
                        int length = typeArguments.length;
                        if (baseTypeParametersLength == length) {
                            for (int k = 0; k < length; k++) {
                                Class<?> argumentClass = asClass(typeArguments[k]);
                                if (argumentClass != null) {
                                    actualTypeArguments.add(i, argumentClass);
                                    break MID;
                                }
                            }
                        }
                    }
                }
            }
        }


        return actualTypeArguments;
    }

    public static <T> Class<T> findActualTypeArgumentClass(Type type, Class<?> interfaceClass, int index) {
        return (Class<T>) findActualTypeArgumentClasses(type, interfaceClass).get(index);
    }

    public static List<Class<?>> findActualTypeArgumentClasses(Type type, Class<?> interfaceClass) {

        List<Type> actualTypeArguments = findActualTypeArguments(type, interfaceClass);

        List<Class<?>> actualTypeArgumentClasses = new LinkedList<>();

        for (Type actualTypeArgument : actualTypeArguments) {
            Class<?> rawClass = getRawClass(actualTypeArgument);
            if (rawClass != null) {
                actualTypeArgumentClasses.add(rawClass);
            }
        }

        return unmodifiableList(actualTypeArgumentClasses);
    }

    /**
     * Get the specified types' generic types(including super classes and interfaces) that are assignable from {@link ParameterizedType} interface
     *
     * @param type        the specified type
     * @param typeFilters one or more {@link Predicate}s to filter the {@link ParameterizedType} instance
     * @return non-null read-only {@link List}
     */
    public static List<ParameterizedType> getGenericTypes(Type type, Predicate<ParameterizedType>... typeFilters) {

        Class<?> rawClass = getRawClass(type);

        if (rawClass == null) {
            return emptyList();
        }

        List<Type> genericTypes = new LinkedList<>();

        genericTypes.add(rawClass.getGenericSuperclass());
        genericTypes.addAll(asList(rawClass.getGenericInterfaces()));

        return unmodifiableList(filterList(genericTypes, TypeUtils::isParameterizedType).stream().map(ParameterizedType.class::cast).filter(and(typeFilters)).collect(toList()));
    }

    public static List<Type> findAllTypes(Type type, Predicate<Type>... typeFilters) {
        List<Type> allGenericTypes = newLinkedList();
        Predicate filter = and(typeFilters);
        if (filter.test(type)) {
            // add self
            allGenericTypes.add(type);
        }
        // Add all hierarchical types in declaration order
        findAllHierarchicalTypes(allGenericTypes, type, filter);
        return unmodifiableList(allGenericTypes);

    }

    public static List<Type> findAllHierarchicalTypes(Type type) {
        return findAllHierarchicalTypes(type, of());
    }

    public static List<Type> findAllHierarchicalTypes(Type type, Predicate<Type>... typeFilters) {
        List<Type> allTypes = newLinkedList();
        findAllHierarchicalTypes(allTypes, type, typeFilters);
        return unmodifiableList(allTypes);
    }

    private static void findAllHierarchicalTypes(List<Type> allTypes, Type type, Predicate<Type>... typeFilters) {
        if (isObjectType(type)) {
            return;
        }

        Class<?> klass = asClass(type);
        if (klass == null) {
            return;
        }

        List<Type> currentTypes = newLinkedList();

        Predicate<? super Type> filter = and(typeFilters);

        Type superType = klass.getGenericSuperclass();

        if (superType != null && filter.test(superType)) { // interface type will return null
            currentTypes.add(superType);
            allTypes.add(superType);
        }

        Type[] interfaceTypes = klass.getGenericInterfaces();
        for (Type interfaceType : interfaceTypes) {
            if (filter.test(interfaceType)) {
                currentTypes.add(interfaceType);
                allTypes.add(interfaceType);
            }
        }

        for (Type currentType : currentTypes) {
            findAllHierarchicalTypes(allTypes, currentType, typeFilters);
        }
    }

    /**
     * Get all generic types(including super classes and interfaces) that are assignable from {@link ParameterizedType} interface
     *
     * @param type        the specified type
     * @param typeFilters one or more {@link Predicate}s to filter the {@link ParameterizedType} instance
     * @return non-null read-only {@link List}
     */
    public static List<ParameterizedType> getAllGenericTypes(Type type, Predicate<ParameterizedType>... typeFilters) {
        List<ParameterizedType> allGenericTypes = new LinkedList<>();
        // Add generic super classes
        allGenericTypes.addAll(getAllGenericSuperClasses(type, typeFilters));
        // Add generic super interfaces
        allGenericTypes.addAll(getAllGenericInterfaces(type, typeFilters));
        // wrap unmodifiable object
        return unmodifiableList(allGenericTypes);
    }

    /**
     * Get all generic super classes that are assignable from {@link ParameterizedType} interface
     *
     * @param type        the specified type
     * @param typeFilters one or more {@link Predicate}s to filter the {@link ParameterizedType} instance
     * @return non-null read-only {@link List}
     */
    public static List<ParameterizedType> getAllGenericSuperClasses(Type type, Predicate<ParameterizedType>... typeFilters) {

        Class<?> rawClass = getRawClass(type);

        if (rawClass == null || rawClass.isInterface()) {
            return emptyList();
        }

        List<Class<?>> allTypes = new LinkedList<>();
        // Add current class
        allTypes.add(rawClass);
        // Add all super classes
        allTypes.addAll(getAllSuperClasses(rawClass, NON_OBJECT_CLASS_FILTER));

        List<ParameterizedType> allGenericSuperClasses = allTypes.stream()
                .map(Class::getGenericSuperclass)
                .filter(TypeUtils::isParameterizedType)
                .map(ParameterizedType.class::cast)
                .collect(Collectors.toList());

        return unmodifiableList(filterAll(allGenericSuperClasses, typeFilters));
    }

    /**
     * Get all generic interfaces that are assignable from {@link ParameterizedType} interface
     *
     * @param type        the specified type
     * @param typeFilters one or more {@link Predicate}s to filter the {@link ParameterizedType} instance
     * @return non-null read-only {@link List}
     */
    public static List<ParameterizedType> getAllGenericInterfaces(Type type, Predicate<ParameterizedType>... typeFilters) {

        Class<?> rawClass = getRawClass(type);

        if (rawClass == null) {
            return emptyList();
        }

        List<Class<?>> allTypes = new LinkedList<>();
        // Add current class
        allTypes.add(rawClass);
        // Add all super classes
        allTypes.addAll(getAllSuperClasses(rawClass, NON_OBJECT_CLASS_FILTER));
        // Add all super interfaces
        allTypes.addAll(ClassUtils.getAllInterfaces(rawClass));

        List<ParameterizedType> allGenericInterfaces = allTypes.stream().map(Class::getGenericInterfaces)
                .map(Arrays::asList)
                .flatMap(Collection::stream)
                .map(TypeUtils::asParameterizedType)
                .filter(Objects::nonNull)
                .collect(toList());

        return unmodifiableList(filterAll(allGenericInterfaces, typeFilters));
    }

    public static String getClassName(Type type) {
        return getRawType(type).getTypeName();
    }

    public static Set<String> getClassNames(Iterable<? extends Type> types) {
        return stream(types.spliterator(), false).map(TypeUtils::getClassName).collect(toSet());
    }

    public static List<Class<?>> resolveTypeArguments(Class<?> targetClass) {
        List<Class<?>> typeArguments = emptyList();
        while (targetClass != null) {
            typeArguments = resolveTypeArgumentsFromInterfaces(targetClass);
            if (!typeArguments.isEmpty()) {
                break;
            }

            Type superType = targetClass.getGenericSuperclass();
            if (superType instanceof ParameterizedType) {
                typeArguments = resolveTypeArgumentsFromType(superType);
            }

            if (!typeArguments.isEmpty()) {
                break;
            }
            // recursively
            targetClass = targetClass.getSuperclass();
        }

        return typeArguments;
    }

    public static List<Class<?>> resolveTypeArgumentsFromInterfaces(Class<?> type) {
        List<Class<?>> typeArguments = emptyList();
        for (Type superInterface : type.getGenericInterfaces()) {
            typeArguments = resolveTypeArgumentsFromType(superInterface);
            if (typeArguments != null && !typeArguments.isEmpty()) {
                break;
            }
        }
        return typeArguments;
    }

    public static List<Class<?>> resolveTypeArgumentsFromType(Type type) {
        List<Class<?>> typeArguments = emptyList();
        if (type instanceof ParameterizedType) {
            typeArguments = new LinkedList<>();
            ParameterizedType pType = (ParameterizedType) type;
            if (pType.getRawType() instanceof Class) {
                for (Type argument : pType.getActualTypeArguments()) {
                    Class<?> typeArgument = asClass(argument);
                    if (typeArgument != null) {
                        typeArguments.add(typeArgument);
                    }
                }
            }
        }
        return typeArguments;
    }

    public static Class<?> asClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return asClass(parameterizedType.getRawType());
        } else if (type instanceof TypeVariable) {
            TypeVariable typeVariable = (TypeVariable) type;
            return asClass(typeVariable.getBounds()[0]);
        }
        return null;
    }

    public static GenericArrayType asGenericArrayType(Type type) {
        if (type instanceof GenericArrayType) {
            return (GenericArrayType) type;
        }
        return null;
    }

    public static ParameterizedType asParameterizedType(Type type) {
        if (isParameterizedType(type)) {
            return (ParameterizedType) type;
        }
        return null;
    }

    public static TypeVariable asTypeVariable(Type type) {
        if (isTypeVariable(type)) {
            return (TypeVariable) type;
        }
        return null;
    }

    public static WildcardType asWildcardType(Type type) {
        if (isWildcardType(type)) {
            return (WildcardType) type;
        }
        return null;
    }

    public static Type getComponentType(Type type) {
        GenericArrayType genericArrayType = asGenericArrayType(type);
        if (genericArrayType != null) {
            return genericArrayType.getGenericComponentType();
        } else {
            Class klass = asClass(type);
            return klass != null ? klass.getComponentType() : null;
        }
    }

    /**
     * Get all super types from the specified type
     *
     * @param type        the specified type
     * @param typeFilters the filters for type
     * @return non-null read-only {@link Set}
     * @since 1.0.0
     */
    public static Set<Type> getAllSuperTypes(Type type, Predicate<Type>... typeFilters) {

        Class<?> rawClass = getRawClass(type);

        if (rawClass == null) {
            return emptySet();
        }

        if (rawClass.isInterface()) {
            return unmodifiableSet(filterAll(singleton(Object.class), typeFilters));
        }

        Set<Type> allSuperTypes = new LinkedHashSet<>();


        Type superType = rawClass.getGenericSuperclass();
        while (superType != null) {
            // add current super class
            allSuperTypes.add(superType);
            Class<?> superClass = getRawClass(superType);
            superType = superClass.getGenericSuperclass();
        }

        return filterAll(allSuperTypes, typeFilters);
    }

    /**
     * Get all super interfaces from the specified type
     *
     * @param type        the specified type
     * @param typeFilters the filters for type
     * @return non-null read-only {@link Set}
     * @since 1.0.0
     */
    public static Set<Type> getAllInterfaces(Type type, Predicate<Type>... typeFilters) {

        Class<?> rawClass = getRawClass(type);

        if (rawClass == null) {
            return emptySet();
        }

        Set<Type> allSuperInterfaces = new LinkedHashSet<>();

        Type[] interfaces = rawClass.getGenericInterfaces();

        // find direct interfaces recursively
        for (Type interfaceType : interfaces) {
            allSuperInterfaces.add(interfaceType);
            allSuperInterfaces.addAll(getAllInterfaces(interfaceType, typeFilters));
        }

        // find super types recursively
        for (Type superType : getAllSuperTypes(type, typeFilters)) {
            allSuperInterfaces.addAll(getAllInterfaces(superType));
        }

        return filterAll(allSuperInterfaces, typeFilters);
    }

    public static Set<Type> getAllTypes(Type type, Predicate<Type>... typeFilters) {

        Set<Type> allTypes = new LinkedHashSet<>();

        // add the specified type
        allTypes.add(type);
        // add all super types
        allTypes.addAll(getAllSuperTypes(type));
        // add all super interfaces
        allTypes.addAll(getAllInterfaces(type));

        return filterAll(allTypes, typeFilters);
    }

    public static Set<ParameterizedType> findParameterizedTypes(Class<?> sourceClass) {
        // Add Generic Interfaces
        List<Type> genericTypes = new LinkedList<>(asList(sourceClass.getGenericInterfaces()));
        // Add Generic Super Class
        genericTypes.add(sourceClass.getGenericSuperclass());

        Set<ParameterizedType> parameterizedTypes = genericTypes.stream()
                .filter(type -> type instanceof ParameterizedType)// filter ParameterizedType
                .map(ParameterizedType.class::cast)  // cast to ParameterizedType
                .collect(Collectors.toSet());

        if (parameterizedTypes.isEmpty()) { // If not found, try to search super types recursively
            genericTypes.stream()
                    .filter(type -> type instanceof Class)
                    .map(Class.class::cast)
                    .forEach(superClass -> parameterizedTypes.addAll(findParameterizedTypes(superClass)));
        }

        return unmodifiableSet(parameterizedTypes);                     // build as a Set

    }
}
