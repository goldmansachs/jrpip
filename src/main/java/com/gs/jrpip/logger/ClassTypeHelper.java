/*
  Copyright 2017 Goldman Sachs.
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
 */

package com.gs.jrpip.logger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClassTypeHelper
{
    private static final Comparator<Field> SORT_BY_FIELD_TYPE = new Comparator<Field>()
    {
        public int compare(Field o1, Field o2)
        {
            if (o1.equals(o2))
            {
                return 0;
            }

            return ClassTypeHelper.isPrintableClass(o1.getType()) ? -1 : 1;
        }
    };

    private static final Comparator<Object> SORT_BY_CLASS_TYPE = new Comparator<Object>()
    {
        public int compare(Object o1, Object o2)
        {
            if (o1.getClass() == o2.getClass())
            {
                return 0;
            }
            return ClassTypeHelper.isPrintableClass(o1.getClass()) ? -1 : 1;
        }
    };

    private static final List<Class<?>> PRINTABLE_CLASS = Arrays.<Class<?>>asList(Byte.class, Boolean.class, Character.class, String.class);

    private ClassTypeHelper()
    {
    }

    public static boolean isCollection(Class<?> aClass)
    {
        return Collection.class.isAssignableFrom(aClass);
    }

    public static boolean isArray(Class<?> array)
    {
        return array.isArray();
    }

    public static boolean isMap(Class<?> aClass)
    {
        return Map.class.isAssignableFrom(aClass);
    }

    public static boolean isPrintableCollection(Class<?> aClass, Field wrapperClassField)
    {
        if (aClass == null || wrapperClassField == null)
        {
            return false;
        }
        return ClassTypeHelper.isCollection(aClass) && ClassTypeHelper.isFieldPrintable(wrapperClassField);
    }

    public static boolean isPrintableMap(Class<?> wrapperClass, Field field)
    {
        if (wrapperClass == null || field == null)
        {
            return false;
        }
        return ClassTypeHelper.isMap(wrapperClass) && ClassTypeHelper.isFieldPrintable(field);
    }

    public static boolean isPrintableArray(Object value)
    {
        boolean isPrintable = false;
        if (value != null && ClassTypeHelper.isArray(value.getClass()) && Array.getLength(value) > 0)
        {
            Object arrayElement = Array.get(value, 0);
            isPrintable = ClassTypeHelper.isArray(value.getClass()) && ClassTypeHelper.isPrintableClass(arrayElement.getClass());
        }
        return isPrintable;
    }

    public static boolean isPrintableClass(Class<?> aClass)
    {
        return aClass.isPrimitive()
                || Date.class.isAssignableFrom(aClass)
                || Number.class.isAssignableFrom(aClass)
                || PRINTABLE_CLASS.contains(aClass);
    }

    public static void sortByFieldType(List<Field> fields)
    {
        Collections.sort(fields, SORT_BY_FIELD_TYPE);
    }

    public static boolean isFieldPrintable(Field field)
    {
        boolean isPrintable = false;
        Type fieldType = field.getGenericType();
        if (fieldType instanceof ParameterizedType)
        {
            Type[] declaredGenericTypes = ((ParameterizedType) fieldType).getActualTypeArguments();
            for (Type type : declaredGenericTypes)
            {
                isPrintable = ClassTypeHelper.isPrintableClass((Class<?>) type);
                if (!isPrintable)
                {
                    break;
                }
            }
        }
        return isPrintable;
    }

    public static List<Field> getAllDeclareFields(
            Class<?> aClass,
            Map<Class<?>, List<Field>> classFieldsMap)
    {
        List<Field> fieldList = classFieldsMap.get(aClass);
        if (fieldList != null && !fieldList.isEmpty())
        {
            return fieldList;
        }

        List<Field> newFieldList = ClassTypeHelper.getAllDeclareFieldsFromClass(aClass);
        classFieldsMap.put(aClass, newFieldList);
        return newFieldList;
    }

    public static Field getDeclaredFieldByName(
            final String fieldName,
            Object wrapperObject,
            Map<Class<?>, List<Field>> classFieldsMap)
    {
        if (fieldName == null || wrapperObject == null)
        {
            return null;
        }

        List<Field> fieldList = ClassTypeHelper.getAllDeclareFields(wrapperObject.getClass(), classFieldsMap);
        for(int i=0;i<fieldList.size();i++)
        {
            Field field = fieldList.get(i);
            if (field.getName().equals(fieldName))
            {
                return field;
            }
        }
        return null;
    }

    protected static List<Field> getAllDeclareFieldsFromClass(Class<?> aClass)
    {
        List<Field> fieldList = new ArrayList<Field>();
        if (aClass == null)
        {
            return fieldList;
        }
        Class<?> startClass = aClass;
        ClassTypeHelper.circularDependencyCheck(aClass);
        while (Object.class != startClass && !ClassTypeHelper.isPrintableClass(startClass))
        {
            addFilteredDeclaredFields(startClass, fieldList);
            startClass = startClass.getSuperclass();
        }
        ClassTypeHelper.sortByFieldType(fieldList);
        return fieldList;
    }

    private static void addFilteredDeclaredFields(Class<?> startClass, List<Field> fieldList)
    {
        Field[] declaredFields = startClass.getDeclaredFields();
        for (Field field: declaredFields)
        {
            if (!(Modifier.isStatic(field.getModifiers())
                    || field.getName().contains("this$")
                    || field.getName().contains("serialVersionUID")))
            {
                fieldList.add(field);
            }
        }
    }

    private static void circularDependencyCheck(Class<?> aClass)
    {
        Class<?> startClass = aClass;
        Map<Integer, Set<String>> classHierarchyMap = new HashMap<Integer, Set<String>>();
        int level = 0;
        while (startClass != Object.class)
        {
            ClassTypeHelper.failIfCircularDependency(classHierarchyMap, startClass.getName(), level);
            Set<String> strings = classHierarchyMap.get(level);
            if (strings == null)
            {
                strings = new HashSet<String>();
                classHierarchyMap.put(level, strings);
            }
            strings.add(startClass.getName());
            startClass = startClass.getSuperclass();
            level++;
        }
    }

    private static void failIfCircularDependency(
            Map<Integer, Set<String>> classHierarchyMap,
            String aClass,
            int classLevel)
    {
        for(Map.Entry<Integer, Set<String>> entry: classHierarchyMap.entrySet())
        {
            if (entry.getKey() != classLevel && entry.getValue().contains(aClass))
            {
                throw new RuntimeException("Detected a circular dependency with class " + aClass);
            }
        }
    }
}
