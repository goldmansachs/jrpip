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

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import com.gs.jrpip.util.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JrpipLog
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JrpipLog.class);
    private final Map<String, String> argumentMap;

    protected JrpipLog(String[] optionsAndValues, Set<String> validOptions)
    {
        this.argumentMap = this.parseArguments(optionsAndValues, validOptions);
    }

    protected FileInputStream openFile(String fileName) throws FileNotFoundException
    {
        try
        {
            return new FileInputStream(fileName);
        }
        catch (FileNotFoundException e)
        {
            LOGGER.error("Unable to open file {}", fileName);
            throw e;
        }
    }

    protected void closeCloseable(Closeable closeable)
    {
        try
        {
            if (closeable != null)
            {
                closeable.close();
            }
        }
        catch (Exception e)
        {
            LOGGER.warn("Unable to close.", e);
        }
    }

    private Map<String, String> parseArguments(String[] args, Set<String> validOptions)
    {
        Map<String, String> argumentMap = new HashMap<String, String>();

        StringBuilder optionValueBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++)
        {
            optionValueBuilder.append(args[i]).append(' ');
            if (i + 1 == args.length || args[i + 1].startsWith("-"))
            {
                int offset = optionValueBuilder.indexOf(" ");
                String option = optionValueBuilder.substring(0, offset).trim();
                String value = optionValueBuilder.substring(offset).trim();
                if (option.isEmpty() || !validOptions.contains(option))
                {
                    throw new RuntimeException("Option " + option + " is not supported!");
                }
                if (value.isEmpty())
                {
                    throw new RuntimeException("Option " + option + " must have value!");
                }
                argumentMap.put(option, value);
                optionValueBuilder.delete(0, optionValueBuilder.length());
            }
        }
        return argumentMap;
    }

    protected String getValueForOption(String option, boolean isOptional)
    {
        String value = this.argumentMap.get(option);
        value = value == null ? "" : value;
        if (!isOptional && value.isEmpty())
        {
            throw new RuntimeException("Unable to parse mandatory option " + option);
        }
        return value;
    }

    protected String parseInputFileName(boolean isOptional)
    {
        return this.getValueForOption("-f", isOptional);
    }

    protected Set<Integer> parseStreamIdSet(boolean isOptional)
    {
        String idsInString = this.getValueForOption("-id", isOptional);
        StringTokenizer st = new StringTokenizer(idsInString, ",");
        Set<Integer> idSet = new HashSet<Integer>();

        try
        {
            while (st.hasMoreTokens())
            {
                idSet.add(Integer.parseInt(st.nextToken().trim()));
            }
        }
        catch (NumberFormatException e)
        {
            LOGGER.error("Unable to parse -id option. Got '-id={}', was expecting '-id=number [,number]'", idsInString, e);
            throw e;
        }
        return idSet;
    }

    protected CustomizedResponseHandler getResponseDataWriter()
    {
        CustomizedResponseHandler logWriter;
        String logWriterConstructString = this.getValueForOption("-writer", true);
        if (logWriterConstructString.isEmpty())
        {
            logWriter = new MockCustomizedResponseHandler();
        }
        else
        {
            logWriter = this.createCustomResponseDataWriter(logWriterConstructString);
        }
        return logWriter;
    }

    private CustomizedResponseHandler createCustomResponseDataWriter(String logWriterConstructString)
    {
        StringTokenizer st = new StringTokenizer(logWriterConstructString, ",");
        String logWriterClassName = st.nextToken();

        Class[] parameterTypes = new Class[st.countTokens()];
        Object[] parameters = new Object[st.countTokens()];
        int cnt = 0;
        while (st.hasMoreTokens())
        {
            String parameter = st.nextToken();
            parameterTypes[cnt] = String.class;
            parameters[cnt] = parameter;
            cnt++;
        }

        CustomizedResponseHandler logWriter;
        try
        {
            Class logWriterClass = Class.forName(logWriterClassName);
            logWriter = (CustomizedResponseHandler) logWriterClass.getConstructor(parameterTypes).newInstance(parameters);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unable to create LogWriter using [" + logWriterConstructString + "].", e);
        }

        return logWriter;
    }

    protected static class LazyFilteredIterator<T> implements Iterator<T>
    {
        private final Iterator<T> delegate;
        private T next;
        private final Predicate<T> discriminator;

        protected LazyFilteredIterator(Iterator<T> delegate, Predicate<T> discriminator)
        {
            this.delegate = delegate;
            this.discriminator = discriminator;
            this.findNext();
        }

        @Override
        public boolean hasNext()
        {
            return this.next != null;
        }

        @Override
        public T next()
        {
            T val = this.next;
            this.next = null;
            this.findNext();
            return val;
        }

        private void findNext()
        {
            while (this.delegate.hasNext() && this.next == null)
            {
                T candidate = this.delegate.next();
                if (this.discriminator.accept(candidate))
                {
                    this.next = candidate;
                }
            }
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
