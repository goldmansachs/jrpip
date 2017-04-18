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

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import com.gs.jrpip.util.stream.readback.RequestData;

public class RequestDataDetailWriter
{
    public static final SimpleDateFormat DEFAULT_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final PrintWriter pw;
    private final SimpleDateFormat dateFormatter;

    public RequestDataDetailWriter(boolean isAutoFlush, SimpleDateFormat dateFormatter)
    {
        this.pw = new PrintWriter(System.out, isAutoFlush);
        this.dateFormatter = dateFormatter;
    }

    protected String buildArgumentContent(Object[] arguments)
    {
        StringBuilder sb = new StringBuilder();
        for (Object argument : arguments)
        {
            sb.append('[').append(argument.toString()).append(']');
        }
        return sb.toString();
    }

    public void printHeader()
    {
        this.pw.printf("%-10s|%-30s|%-36s|%-36s|%s\n",
                "ID",
                "Method Name",
                "Start (time in long)",
                "End (time in long)",
                "Arguments");
    }

    public void write(Iterator<RequestData> requests)
    {
        this.printHeader();
        while (requests.hasNext())
        {
            RequestData requestData = requests.next();
            String startTimeStr = this.dateFormatter.format(new Date(requestData.getStartTime()));
            String endTimeStr = this.dateFormatter.format(new Date(requestData.getEndTime()));
            this.pw.printf(Locale.getDefault(),
                    "%-10s|%-30s|%-20s (%s)|%-20s (%s)|%s\n",
                    requestData.getStreamId(),
                    requestData.getMethodName(),
                    startTimeStr,
                    requestData.getStartTime(),
                    endTimeStr,
                    requestData.getEndTime(),
                    this.buildArgumentContent(requestData.getArguments()));
        }
    }
}
