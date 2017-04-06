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

package com.gs.jrpip.util.stream.readback;

import com.gs.jrpip.util.Predicate;
import org.junit.Assert;
import org.junit.Test;

public class PredicateFactoryTest
{
    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private final ListJrpipPredicateFactory discriminatorFactory = ListJrpipPredicateFactory.getInstance();

    @Test
    public void testIdDiscriminator()
    {
        Predicate<RequestData> idDiscriminator = this.discriminatorFactory.createIdDiscriminator(1, 5);
        Assert.assertTrue(idDiscriminator.accept(new RequestData(1, null, "test", EMPTY_OBJECT_ARRAY, 0L, 0L)));
        Assert.assertTrue(idDiscriminator.accept(new RequestData(5, null, "test", EMPTY_OBJECT_ARRAY, 0L, 0L)));
        Assert.assertFalse(idDiscriminator.accept(new RequestData(50, null, "test", EMPTY_OBJECT_ARRAY, 0L, 0L)));
    }

    @Test
    public void testTimeFromDiscriminator()
    {
        Predicate<RequestData> timeFromDiscriminator = this.discriminatorFactory.createTimeFromDiscriminator(10L);
        Assert.assertTrue(timeFromDiscriminator.accept(new RequestData(10, null, "test", EMPTY_OBJECT_ARRAY, 10L, 100L)));
        Assert.assertTrue(timeFromDiscriminator.accept(new RequestData(10, null, "test", EMPTY_OBJECT_ARRAY, 100L, 100L)));
        Assert.assertFalse(timeFromDiscriminator.accept(new RequestData(10, null, "test", EMPTY_OBJECT_ARRAY, 0L, 100L)));
    }

    @Test
    public void testTimeToDiscriminator()
    {
        Predicate<RequestData> timeToDiscriminator = ListJrpipPredicateFactory.getInstance().createTimeToDiscriminator(10L);
        Assert.assertTrue(timeToDiscriminator.accept(new RequestData(10, null, "test", EMPTY_OBJECT_ARRAY, 10L, 100L)));
        Assert.assertFalse(timeToDiscriminator.accept(new RequestData(10, null, "test", EMPTY_OBJECT_ARRAY, 100L, 100L)));
        Assert.assertTrue(timeToDiscriminator.accept(new RequestData(10, null, "test", EMPTY_OBJECT_ARRAY, 0L, 100L)));
    }

    @Test
    public void testNameDiscriminator()
    {
        Predicate<RequestData> methodNameDiscriminator = this.discriminatorFactory.createMethodNameDiscriminator("[abc]");
        Assert.assertTrue(methodNameDiscriminator.accept(new RequestData(1, null, "a", EMPTY_OBJECT_ARRAY, 0L, 0L)));
        Assert.assertFalse(methodNameDiscriminator.accept(new RequestData(50, null, "q", EMPTY_OBJECT_ARRAY, 0L, 0L)));
    }
}
