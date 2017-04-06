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

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class ClassTypeHelperTest
{
    @Test
    public void testIsPrintable() throws Exception
    {
        Assert.assertTrue(ClassTypeHelper.isPrintableClass(Integer.class));
        Assert.assertTrue(ClassTypeHelper.isPrintableClass(float.class));
        Assert.assertTrue(ClassTypeHelper.isPrintableClass(Timestamp.class));
        Assert.assertTrue(ClassTypeHelper.isPrintableClass(Date.class));
        Assert.assertFalse(ClassTypeHelper.isPrintableClass(HashMap.class));
    }

    @Test
    public void testPrintableCollections() throws Exception
    {
        PrintableCollectionAndMap collectionAndMap = new PrintableCollectionAndMap();
        List<Field> printable = ClassTypeHelper.getAllDeclareFieldsFromClass(collectionAndMap.getClass());
        for (Field field : printable)
        {
            Object value = field.get(collectionAndMap);
            if (ClassTypeHelper.isCollection(value.getClass()))
            {
                Assert.assertTrue(ClassTypeHelper.isPrintableCollection(value.getClass(), field));
            }
            else if (ClassTypeHelper.isArray(value.getClass()))
            {
                Assert.assertTrue(ClassTypeHelper.isPrintableArray(value));
            }
            else if (ClassTypeHelper.isMap(value.getClass()))
            {
                Assert.assertTrue(ClassTypeHelper.isPrintableMap(value.getClass(), field));
            }
            else
            {
                Assert.fail("Should not be here");
            }
        }
    }

    @Test
    public void testNonPrintableCollections() throws Exception
    {
        NotPrintableCollectionAndMap collectionAndMap = new NotPrintableCollectionAndMap();
        List<Field> printable = ClassTypeHelper.getAllDeclareFieldsFromClass(collectionAndMap.getClass());
        for (Field field : printable)
        {
            Object value = field.get(collectionAndMap);
            Class valueClass = value.getClass();
            if (ClassTypeHelper.isCollection(valueClass))
            {
                Assert.assertFalse(ClassTypeHelper.isPrintableCollection(valueClass, field));
            }
            else if (ClassTypeHelper.isArray(valueClass))
            {
                Assert.assertFalse(ClassTypeHelper.isPrintableArray(value));
            }
            else if (ClassTypeHelper.isMap(valueClass))
            {
                Assert.assertFalse(ClassTypeHelper.isPrintableMap(valueClass, field));
            }
            else
            {
                Assert.fail("Should not be here");
            }
        }
    }

    @SuppressWarnings({"PublicField", "UnusedDeclaration", "PackageVisibleField"})
    private static class NotPrintableCollectionAndMap
    {
        public Collection<Math> arrayList = new ArrayList<Math>();
        protected Map<String, Math> hashMap = new HashMap<String, Math>();
        protected Calendar[] calendarArray = {Calendar.getInstance(), Calendar.getInstance()};
        Collection<Math> unifiedSet = new HashSet<Math>();
        Map<Math, String> unifiedMap = new HashMap<Math, String>();
    }

    @SuppressWarnings({"PublicField", "UnusedDeclaration", "PackageVisibleField"})
    private static class PrintableCollectionAndMap
    {
        public Collection<String> arrayList = new ArrayList<String>();
        protected Map<String, String> hashMap = new HashMap<String, String>();
        protected int[] intArray = {1, 2, 3, 4};
        Collection<String> unifiedSet = new HashSet<String>();
        Map<String, String> unifiedMap = new HashMap<String, String>();
    }
}
