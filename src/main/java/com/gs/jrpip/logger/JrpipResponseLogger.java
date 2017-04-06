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
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.gs.jrpip.util.stream.readback.ResponseData;
import com.gs.jrpip.util.stream.readback.ResponseDataMultiStreamIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JrpipResponseLogger extends JrpipLog
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JrpipResponseLogger.class);
    private static final HashSet<String> VALID_ARGS = new HashSet<String>();
    private final String inputFilename;
    private final Set<Integer> streamIds;

    static
    {
        VALID_ARGS.add("-f");
        VALID_ARGS.add("-id");
        VALID_ARGS.add("-writer");
    }

    public JrpipResponseLogger(String[] arguments)
    {
        super(arguments, VALID_ARGS);
        this.inputFilename = this.getValueForOption("-f", false);
        this.streamIds = this.parseStreamIdSet(false);
    }

    public void extractResponses()
    {
        FileInputStream inputStream = null;
        try
        {
            LOGGER.info("Opening input log file {}", this.inputFilename);
            inputStream = this.openFile(this.inputFilename);
            this.writeResponse(this.streamIds, inputStream);
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException("Unable to extract from " + this.inputFilename, e);
        }
        finally
        {
            this.closeCloseable(inputStream);
        }
    }

    protected void writeResponse(Set<Integer> streamIds, FileInputStream inputStream)
    {
        ResponseParserWriter responseParserWriter = null;
        try
        {
            String outFile = this.getOutputFileName();
            LOGGER.info("Writing extract data to {}", outFile);
            responseParserWriter = new ResponseParserWriter();
            responseParserWriter.startDocument(outFile);
            CustomizedResponseHandler logWriter = this.getResponseDataWriter();

            Iterator<ResponseData> iterator = new ResponseDataMultiStreamIterable(inputStream, streamIds).iterator();

            while (iterator.hasNext())
            {
                ResponseData response = iterator.next();
                responseParserWriter.writeResponse(response, logWriter);
            }
        }
        finally
        {
            if (responseParserWriter != null)
            {
                responseParserWriter.endDocument();
            }
        }
    }

    private String getOutputFileName()
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MMM.dd");
        Date currentDate = new Date(System.currentTimeMillis());
        return this.inputFilename + '_' + currentDate.getTime() + '_' + dateFormat.format(new Date(System.currentTimeMillis())) + ".xml";
    }

    private static void printUsage()
    {
        String usageStr =
                "Usage:\n"
                        + "   JrpipResponseLogger -f inputFileName -id number [-outdir outputDirectory] [-writer full.class.name]\n"
                        + "   Options:\n"
                        + "       -id number [,number]\n"
                        + "       -outDir outputDirectory\n"
                        + "       -writer className [argument]\n"
                        + "   Example:\n"
                        + "       JrpipResponseLogger -f myJrpip.log -id 11,23 -writer com.gs.jrpip.util.logging.LogWriter,argument1,argument2\n"
                        + "\n\n\n";
        System.out.print(usageStr);
    }

    public static void main(String[] args)
    {
        JrpipResponseLogger responseWriter;
        try
        {
            responseWriter = new JrpipResponseLogger(args);
        }
        catch (Exception e)
        {
            JrpipResponseLogger.printUsage();
            throw new RuntimeException(e);
        }

        responseWriter.extractResponses();
    }
}
