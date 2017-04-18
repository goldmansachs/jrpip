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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gs.jrpip.util.stream.readback.ResponseData;

public class ResponseParserWriter
{
    private XmlFileWriter jrpipXmlWriter;
    private final Map<Class<?>, List<Field>> fieldsByClassMap = new HashMap<Class<?>, List<Field>>();

    public void startDocument(String filename)
    {
        try
        {
            this.jrpipXmlWriter = new XmlFileWriter();
            this.jrpipXmlWriter.setIndent(true);
            this.jrpipXmlWriter.openFile(filename);
            this.jrpipXmlWriter.startPrinting("Data", "");
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to create output file " + filename, e);
        }
    }

    public void endDocument()
    {
        try
        {
            this.jrpipXmlWriter.stopPrinting("Data");
            this.jrpipXmlWriter.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to close the output file", e);
        }
    }

    protected void writeRequest(ResponseData responseData, CustomizedResponseHandler logWriter) throws IllegalAccessException, IOException
    {
        this.jrpipXmlWriter.writeStartTag("request");
        Object[] arguments = responseData.getRequestData().getArguments();
        for (Object argument : arguments)
        {
            this.convertDataToXml("", argument, null, logWriter);
        }
        this.jrpipXmlWriter.writeEndTag("request");
    }

    protected void writeResponse(ResponseData responseData, CustomizedResponseHandler logWriter)
    {
        try
        {
            int id = responseData.getRequestData().getStreamId();
            this.startResponseBlock(id);
            this.writeRequest(responseData, logWriter);
            this.convertDataToXml("", responseData.getReturnedData(), null, logWriter);
            this.endResponseBlock();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected void convertDataToXml(
            String name,
            Object data,
            Object wrapperObj,
            CustomizedResponseHandler logWriter) throws IllegalAccessException
    {
        if (data == null)
        {
            return;
        }

        if (name == null)
        {
            name = "";
        }

        Class dataClass = data.getClass();

        List<Field> allFields = ClassTypeHelper.getAllDeclareFields(data.getClass(), this.fieldsByClassMap);
        Field dataFieldFromWrapper = ClassTypeHelper.getDeclaredFieldByName(name, wrapperObj, this.fieldsByClassMap);

        if (logWriter.handlesObject(data))
        {
            logWriter.writeObject(data, this.jrpipXmlWriter);
        }
        else if (ClassTypeHelper.isPrintableClass(dataClass))
        {
            this.jrpipXmlWriter.printObject(dataClass.getSimpleName(), data);
        }
        else if (ClassTypeHelper.isPrintableCollection(data.getClass(), dataFieldFromWrapper))
        {
            this.jrpipXmlWriter.printCollection(name, data);
        }
        else if (ClassTypeHelper.isPrintableArray(data))
        {
            this.jrpipXmlWriter.printArray(name, data);
        }
        else if (ClassTypeHelper.isCollection(dataClass))
        {
            this.unwrapCollectionForWrite(logWriter, data, name);
        }
        else if (ClassTypeHelper.isMap(data.getClass()))
        {
            this.unwrapMapForWrite(logWriter, data, name);
        }
        else
        {
            this.unwrapSimpleClassForWrite(allFields, logWriter, data, name);
        }
    }

    private void unwrapCollectionForWrite(
            CustomizedResponseHandler logWriter,
            Object data,
            String name) throws IllegalAccessException
    {
        this.jrpipXmlWriter.startPrinting(data.getClass().getSimpleName(), name);
        Iterable myCollection = (Iterable) data;
        for (Object each : myCollection)
        {
            this.convertDataToXml("", each, data, logWriter);
        }
        this.jrpipXmlWriter.stopPrinting(data.getClass().getSimpleName());
    }

    private void unwrapSimpleClassForWrite(
            Iterable<Field> allFields,
            CustomizedResponseHandler logWriter,
            Object data,
            String name) throws IllegalAccessException
    {
        this.jrpipXmlWriter.startPrinting(data.getClass().getSimpleName(), name);
        for (Field current : allFields)
        {
            current.setAccessible(true);
            Object value = current.get(data);
            if (ClassTypeHelper.isPrintableClass(current.getType()))
            {
                if (value != null)
                {
                    this.jrpipXmlWriter.printAttribute(current.getName(), value);
                }
            }
            else
            {
                this.convertDataToXml(current.getName(), value, data, logWriter);
            }
        }
        this.jrpipXmlWriter.stopPrinting(data.getClass().getSimpleName());
    }

    private void unwrapMapForWrite(
            CustomizedResponseHandler logWriter,
            Object data,
            String name) throws IllegalAccessException
    {
        this.jrpipXmlWriter.startPrinting(data.getClass().getSimpleName(), name);
        for (Map.Entry<Object, Object> e : ((Map<Object, Object>) data).entrySet())
        {
            this.jrpipXmlWriter.startPrinting("key", "");
            this.convertDataToXml("", e.getKey(), data, logWriter);
            this.jrpipXmlWriter.stopPrinting("key");

            this.jrpipXmlWriter.startPrinting("value", "");
            this.convertDataToXml("", e.getValue(), data, logWriter);
            this.jrpipXmlWriter.stopPrinting("value");
        }
        this.jrpipXmlWriter.stopPrinting(data.getClass().getSimpleName());
    }

    private void startResponseBlock(long streamId)
    {
        try
        {
            this.jrpipXmlWriter.writeStartTag("response");
            this.jrpipXmlWriter.writeAttribute("streamId", String.valueOf(streamId));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to write start response block with stream id " + streamId, e);
        }
    }

    private void endResponseBlock()
    {
        try
        {
            this.jrpipXmlWriter.writeEndTag("response");
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to close response block", e);
        }
    }
}
