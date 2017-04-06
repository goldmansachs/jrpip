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
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class XmlFileWriter implements ResponseXmlWriter
{
    private EscapedAppendable appendable;
    private Closeable toCloseAtEnd;
    private int currentDepth;
    private int indentWidth = 4;
    private boolean tagIsOpen;
    private boolean indent;
    private boolean indentSafe;

    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final DecimalFormat decimalFormatter = new DecimalFormat("#0.####");

    private final List<Class> numericPrimitives = Arrays.<Class>asList(
            short.class, int.class, long.class, float.class, double.class
    );

    public void setIndentWidth(int width)
    {
        this.indentWidth = width;
    }

    public void setIndent(boolean indent)
    {
        this.indent = indent;
    }

    protected void setAppendable(Appendable appendable)
    {
        this.appendable = new EscapedAppendable(appendable);
    }

    protected void openFile(String filename) throws IOException
    {
        FileWriter writer = new FileWriter(filename);
        this.appendable = new EscapedAppendable(writer);
        this.toCloseAtEnd = writer;
    }

    protected void close() throws IOException
    {
        this.appendable = null;
        this.currentDepth = 0;
        this.indentSafe = false;
        this.tagIsOpen = false;
        if (this.toCloseAtEnd != null)
        {
            this.toCloseAtEnd.close();
        }
    }

    public void writeIndent(boolean startTag) throws IOException
    {
        if (this.indent && this.indentSafe)
        {
            if (this.currentDepth > 0 || !startTag)
            {
                this.appendable.rawAppend("\n");
            }
            for (int i = 0; i < this.currentDepth * this.indentWidth; i++)
            {
                this.appendable.rawAppend(" ");
            }
        }
    }

    public void writeStartTag(String tagName) throws IOException
    {
        if (this.tagIsOpen)
        {
            this.appendable.rawAppend(">");
        }

        this.writeIndent(true);
        this.currentDepth++;
        this.appendable.rawAppend("<").rawAppend(tagName);
        this.tagIsOpen = true;
        this.indentSafe = true;
    }

    public void writeSimpleTag(String tagName, String value) throws IOException
    {
        this.writeStartTag(tagName);
        this.writeContent(value);
        this.writeEndTag(tagName);
    }

    public void writeAttribute(String attributeName, String attributeValue) throws IOException
    {
        this.appendable.rawAppend(" ").rawAppend(attributeName).rawAppend("=\"").append(attributeValue).rawAppend("\"");
    }

    public void writeEndTag(String tagName) throws IOException
    {
        this.currentDepth--;
        if (this.tagIsOpen)
        {
            this.appendable.rawAppend("/>");
        }
        else
        {
            this.writeIndent(false);
            this.appendable.rawAppend("</").rawAppend(tagName).rawAppend(">");
        }

        this.tagIsOpen = false;
        this.indentSafe = true;
    }

    public void writeContent(String content) throws IOException
    {
        this.indentSafe = false;
        this.tagIsOpen = false;
        this.appendable.rawAppend(">").append(content);
    }

    @Override
    public void startPrinting(String elementName, String attributeName)
    {
        elementName = elementName == null ? "" : elementName.trim();
        attributeName = attributeName == null ? "" : attributeName.trim();
        try
        {
            this.writeStartTag(elementName);
            if (!attributeName.isEmpty())
            {
                this.writeAttribute("name", attributeName);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to write start element " + elementName, e);
        }
    }

    @Override
    public void stopPrinting(String name)
    {
        try
        {
            this.writeEndTag(name);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to close element " + name, e);
        }
    }

    @Override
    public void printAttribute(String name, Object value)
    {
        try
        {
            this.writeAttribute(name, this.getStringValueFrom(value));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to write attribute " + name + " : " + value, e);
        }
    }

    @Override
    public void printCollection(String name, Object values)
    {
        if (name == null || values == null)
        {
            return;
        }

        try
        {
            this.writeStartTag(values.getClass().getSimpleName());
            this.writeAttribute("name", name);
            this.writeAttribute("value", this.getStringFromCollection((Collection) values));
            this.writeEndTag(values.getClass().getSimpleName());
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to write collection " + name, e);
        }
    }

    public void printObject(String name, Object value)
    {
        if (name == null || value == null)
        {
            return;
        }

        try
        {
            this.writeStartTag(name);
            this.writeAttribute("value", this.getStringValueFrom(value));
            this.writeEndTag(name);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to write object data", e);
        }
    }

    public void printArray(String name, Object values)
    {
        if (name == null || values == null)
        {
            return;
        }

        try
        {
            this.writeStartTag("array");
            this.writeAttribute("name", name);
            this.writeAttribute("value", this.getStringFromArray(values));
            this.writeEndTag("array");
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to write array " + name, e);
        }
    }

    public String getStringValueFrom(Object obj)
    {
        String formattedString;
        if (this.isNumericClass(obj.getClass()))
        {
            formattedString = this.decimalFormatter.format(obj);
        }
        else if (Date.class.isAssignableFrom(obj.getClass()))
        {
            formattedString = this.dateFormatter.format(obj);
        }
        else
        {
            formattedString = obj.toString();
        }
        return formattedString;
    }

    public String getStringFromArray(Object array)
    {
        StringBuilder sb = new StringBuilder();
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++)
        {
            Object arrayElement = Array.get(array, i);
            if (arrayElement != null)
            {
                sb.append(this.getStringValueFrom(arrayElement));
                if (i + 1 < length)
                {
                    sb.append(',');
                }
            }
        }
        return sb.toString();
    }

    public String getStringFromCollection(Collection collection)
    {
        StringBuilder builder = new StringBuilder();
        for(Object o: collection)
        {
            if (builder.length() > 0)
            {
                builder.append(',');
            }
            builder.append(this.getStringValueFrom(o));
        }
        return builder.toString();
    }

    protected boolean isNumericClass(Class aClass)
    {
        return this.numericPrimitives.contains(aClass) || Number.class.isAssignableFrom(aClass);
    }

    private static final class EscapedAppendable
    {
        private final Appendable inner;

        private EscapedAppendable(Appendable inner)
        {
            this.inner = inner;
        }

        public EscapedAppendable append(char c) throws IOException
        {
            if (c == '<')
            {
                this.inner.append("&lt;");
            }
            else if (c == '>')
            {
                this.inner.append("&gt;");
            }
            else if (c == '&')
            {
                this.inner.append("&amp;");
            }
            else if (c == '\'')
            {
                this.inner.append("&apos;");
            }
            else if (c == '"')
            {
                this.inner.append("&quot;");
            }
            else if (c == '\t')
            {
                this.inner.append("&#x9;");
            }
            else if (c > 127)
            {
                this.inner.append("&#").append(Integer.toString(c)).append(";");
            }
            else
            {
                this.inner.append(c);
            }

            return this;
        }

        public EscapedAppendable append(CharSequence csq) throws IOException
        {
            if (csq == null)
            {
                return this;
            }

            for (int i = 0; i < csq.length(); i++)
            {
                this.append(csq.charAt(i));
            }
            return this;
        }

        public EscapedAppendable append(CharSequence csq, int start, int end) throws IOException
        {
            for (int i = start; i < end; i++)
            {
                this.append(csq.charAt(i));
            }
            return this;
        }

        public EscapedAppendable rawAppend(CharSequence csq) throws IOException
        {
            this.inner.append(csq);
            return this;
        }
    }
}
