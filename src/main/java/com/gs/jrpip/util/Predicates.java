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

package com.gs.jrpip.util;

import java.util.ArrayList;
import java.util.List;

public class Predicates
{
    public static final Predicate TRUE_PREDICATE = new Predicate()
    {
        @Override
        public boolean accept(Object o)
        {
            return true;
        }
    };

    public static final Predicate and(Predicate one, Predicate two)
    {
        return new AndPredicate(one, two);
    }

    private static class AndPredicate implements Predicate
    {
        private List<Predicate> predicates = new ArrayList<Predicate>(4);

        public AndPredicate(Predicate one, Predicate two)
        {
            addPredicate(one);
            addPredicate(two);
        }

        private void addPredicate(Predicate one)
        {
            if (one instanceof AndPredicate)
            {
                predicates.addAll(((AndPredicate) one).predicates);
            }
            else
            {
                predicates.add(one);
            }
        }

        @Override
        public boolean accept(Object o)
        {
            for(int i=0;i<predicates.size();i++)
            {
                if (!predicates.get(i).accept(o))
                {
                    return false;
                }
            }
            return true;
        }
    }
}
