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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import com.gs.jrpip.util.Predicate;
import com.gs.jrpip.util.Predicates;

public final class ListJrpipPredicateFactory
{
    private static final ListJrpipPredicateFactory FACTORY = new ListJrpipPredicateFactory();
    private final Map<String, Function<List<String>, Predicate<RequestData>>> filterBuilders = new HashMap<String, Function<List<String>, Predicate<RequestData>>>();

    private ListJrpipPredicateFactory()
    {
        this.filterBuilders.put("-id", new Function<List<String>, Predicate<RequestData>>()
        {
            public Predicate<RequestData> valueOf(List<String> object)
            {
                return ListJrpipPredicateFactory.this.parseAndCreateIdOperation(object);
            }
        });

        this.filterBuilders.put("-method", new Function<List<String>, Predicate<RequestData>>()
        {
            public Predicate<RequestData> valueOf(List<String> object)
            {
                return ListJrpipPredicateFactory.this.createMethodNameDiscriminator(object.get(0));
            }
        });

        this.filterBuilders.put("-between", new Function<List<String>, Predicate<RequestData>>()
        {
            public Predicate<RequestData> valueOf(List<String> object)
            {
                return ListJrpipPredicateFactory.this.createTimeRangeOperation(object);
            }
        });

        this.filterBuilders.put("-argument", new Function<List<String>, Predicate<RequestData>>()
        {
            public Predicate<RequestData> valueOf(List<String> object)
            {
                return ListJrpipPredicateFactory.this.createArgumentDiscriminator(object.get(0));
            }
        });
    }

    public static ListJrpipPredicateFactory getInstance()
    {
        return FACTORY;
    }

    public Predicate<RequestData> getFilter(String optionId, String optionValue)
    {
        List<String> optionValueList = this.parseArguments(",", optionValue);
        Function<List<String>, Predicate<RequestData>> filter = this.filterBuilders.get(optionId);
        if (filter == null)
        {
            throw new RuntimeException("Argument name " + optionId + " is not valid");
        }
        return filter.valueOf(optionValueList);
    }

    Predicate<RequestData> createIdDiscriminator(int... ids)
    {
        final Set<Integer> streamIds = new HashSet<Integer>();
        for (int i = 0; i < ids.length; i++)
        {
            streamIds.add(ids[i]);
        }

        return new Predicate<RequestData>()
        {
            public boolean accept(RequestData requestData)
            {
                return streamIds.contains(requestData.getStreamId());
            }
        };
    }

    Predicate<RequestData> createTimeFromDiscriminator(final long timeFrom)
    {
        return new Predicate<RequestData>()
        {
            public boolean accept(RequestData requestData)
            {
                return requestData.getStartTime() >= timeFrom;
            }
        };
    }

    Predicate<RequestData> createTimeToDiscriminator(final long timeTo)
    {
        return new Predicate<RequestData>()
        {
            public boolean accept(RequestData requestData)
            {
                return requestData.getStartTime() <= timeTo;
            }
        };
    }

    Predicate<RequestData> createMethodNameDiscriminator(final String regex)
    {
        return new Predicate<RequestData>()
        {
            private final RegexMatcher regexMatcher = new RegexMatcher();

            public boolean accept(RequestData requestData)
            {
                return this.regexMatcher.findMatch(requestData.getMethodName(), regex);
            }
        };
    }

    private Predicate<RequestData> createArgumentDiscriminator(final String regex)
    {
        return new Predicate<RequestData>()
        {
            private boolean hasMatch(Object element, RegexMatcher matcher)
            {
                boolean found = false;
                Iterable iterableArgument = ListJrpipPredicateFactory.this.convertToIterable(element);
                Object[] arrayArgument = ListJrpipPredicateFactory.this.convertToObjectArray(element);

                if (iterableArgument != null)
                {
                    for (Iterator i = iterableArgument.iterator(); i.hasNext() && !found; )
                    {
                        found = this.hasMatch(i.next(), matcher);
                    }
                }
                else if (arrayArgument != null)
                {
                    for (int i = 0; i < arrayArgument.length && !found; i++)
                    {
                        found = this.hasMatch(arrayArgument[i], matcher);
                    }
                }
                else if (!element.getClass().isArray()) // ignore primitive array for now.
                {
                    found = matcher.findMatch(element.toString(), regex);
                }
                return found;
            }

            public boolean accept(RequestData requestData)
            {
                return this.hasMatch(requestData.getArguments(), new RegexMatcher());
            }
        };
    }

    private Predicate<RequestData> parseAndCreateIdOperation(List<String> argument)
    {
        int[] searchIds = new int[argument.size()];
        int cnt = 0;
        for (String argValue : argument)
        {
            searchIds[cnt++] = Integer.parseInt(argValue);
        }
        return this.createIdDiscriminator(searchIds);
    }

    private List<String> parseArguments(String delim, String value)
    {
        StringTokenizer st = new StringTokenizer(value, delim);
        List<String> argValueList = new ArrayList<String>();
        while (st.hasMoreTokens())
        {
            argValueList.add(st.nextToken());
        }
        return argValueList;
    }

    private Predicate<RequestData> createTimeRangeOperation(List<String> fromToInLong)
    {
        Predicate<RequestData> timeOperate = null;
        if (fromToInLong.size() >= 1)
        {
            timeOperate = this.createTimeFromDiscriminator(this.extractTimeInLong(fromToInLong.get(0)));
        }
        if (fromToInLong.size() == 2 && timeOperate != null)
        {
            Predicate<RequestData> to = this.createTimeToDiscriminator(this.extractTimeInLong(fromToInLong.get(1)));
            timeOperate = Predicates.and(timeOperate, to);
        }
        return timeOperate;
    }

    private long extractTimeInLong(String timeString)
    {
        long timeInLong;
        try
        {
            timeInLong = timeString.contains("-") ? Timestamp.valueOf(timeString).getTime() : Long.parseLong(timeString);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unable to parse time string " + timeString, e);
        }
        return timeInLong;
    }

    private Iterable convertToIterable(Object element)
    {
        if (element instanceof Iterable)
        {
            return (Iterable) element;
        }
        return null;
    }

    private Object[] convertToObjectArray(Object element)
    {
        if (element instanceof Object[])
        {
            return (Object[]) element;
        }
        return null;
    }

    protected static class RegexMatcher
    {
        private final Set<String> accept = new HashSet<String>();
        private final Set<String> reject = new HashSet<String>();

        public boolean findMatch(String value, String regex)
        {
            if (this.accept.contains(value))
            {
                return true;
            }
            if (this.reject.contains(value))
            {
                return false;
            }
            boolean result = value.matches(regex);
            if (result)
            {
                this.accept.add(value);
            }
            else
            {
                this.reject.add(value);
            }
            return result;
        }
    }

    private static interface Function<T, V>
    {
        V valueOf(T t);
    }
}
