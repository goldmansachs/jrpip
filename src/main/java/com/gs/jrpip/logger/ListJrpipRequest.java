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

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.gs.jrpip.util.Predicate;
import com.gs.jrpip.util.Predicates;
import com.gs.jrpip.util.stream.readback.ListJrpipPredicateFactory;
import com.gs.jrpip.util.stream.readback.RequestData;
import com.gs.jrpip.util.stream.readback.RequestDataMultiStreamIterable;

public class ListJrpipRequest extends JrpipLog
{
    private static final Set<String> VALID_ARGS = new HashSet<String>();
    private final String inputFilename;
    private final Map<String, String> filters;

    static
    {
        VALID_ARGS.add("-f");
        VALID_ARGS.add("-id");
        VALID_ARGS.add("-method");
        VALID_ARGS.add("-between");
        VALID_ARGS.add("-argument");
    }

    public ListJrpipRequest(String[] arguments)
    {
        super(arguments, VALID_ARGS);
        this.filters = this.parseFilterOptions();
        this.inputFilename = this.parseInputFileName(false);
    }

    public void listContent() throws Exception
    {
        FileInputStream inputStream = null;
        try
        {
            inputStream = this.openFile(this.inputFilename);
            Iterator<RequestData> matchedRequests = new RequestDataMultiStreamIterable(inputStream).iterator();
            if (!this.filters.isEmpty())
            {
                matchedRequests = new LazyFilteredIterator<RequestData>(matchedRequests, this.createMatchOperations(this.filters));
            }
            RequestDataDetailWriter writer = new RequestDataDetailWriter(true, RequestDataDetailWriter.DEFAULT_FORMATTER);
            this.writeResult(writer, matchedRequests);
        }
        finally
        {
            this.closeCloseable(inputStream);
        }
    }

    protected void writeResult(RequestDataDetailWriter writer, Iterator<RequestData> matchedRequests)
    {
        writer.write(matchedRequests);
    }

    protected Predicate<RequestData> createMatchOperations(Map<String, String> optionsValues)
    {
        Predicate<RequestData> filterOperation = Predicates.TRUE_PREDICATE;
        ListJrpipPredicateFactory discriminatorFactory = ListJrpipPredicateFactory.getInstance();
        for (Map.Entry<String, String> entry : optionsValues.entrySet())
        {
            Predicate<RequestData> filter = discriminatorFactory.getFilter(entry.getKey(), entry.getValue());
            filterOperation = Predicates.and(filterOperation, filter);
        }
        return filterOperation;
    }

    private Map<String, String> parseFilterOptions()
    {
        Map<String, String> filters = new HashMap<String, String>();
        String[] options = {"-id", "-method", "-between", "-argument"};
        for (String option : options)
        {
            String value = this.getValueForOption(option, true);
            if (value != null && !value.isEmpty())
            {
                filters.put(option, value);
            }
        }
        return filters;
    }

    private static void printUsage()
    {
        PrintWriter pw = new PrintWriter(System.out, true);
        String usageStr =
                "Usage:\n"
                        + "   ListJrpipRequest -f inputFileName [OPTIONS]\n\n"
                        + "   Options:\n"
                        + "       -id number [,number]\n"
                        + "       -method name\n"
                        + "       -between start, end [dates formatted as yyyy-MM-dd HH:mm:ss]\n"
                        + "       -argument value\n\n"
                        + "   Example:\n"
                        + "       ListJrpipRequest -f inputFile.log\n"
                        + "       ListJrpipRequest -f inputFile.log -id 11,23 -method myMethod -between 2011-05-30 13:00:00, 2011-05-30 13:05:00 -argument GSCO\n"
                        + "\n\n\n";

        pw.printf(usageStr);
    }

    public static void main(String[] args) throws Exception
    {
        ListJrpipRequest logLister;
        try
        {
            logLister = new ListJrpipRequest(args);
        }
        catch (Exception e)
        {
            ListJrpipRequest.printUsage();
            throw e;
        }
        logLister.listContent();
    }
}

