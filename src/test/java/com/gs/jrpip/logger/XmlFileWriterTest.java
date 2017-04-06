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

import java.io.File;
import java.sql.Timestamp;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.gs.jrpip.JrpipTestCase;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlFileWriterTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testXmlFileCreation() throws Exception
    {
        File logfile = this.folder.newFile();
        String logfileStr = logfile.getCanonicalPath();

        XmlFileWriter xmlWriter = new XmlFileWriter();
        xmlWriter.setIndent(false);
        xmlWriter.setIndentWidth(4);
        try
        {
            xmlWriter.openFile(logfileStr);
            xmlWriter.startPrinting("ROOT", "root_element");
            xmlWriter.stopPrinting("ROOT");
        }
        finally
        {
            xmlWriter.close();
        }
        Assert.assertTrue(logfile.exists());
    }

    @Test
    public void testContinuousWriting() throws Exception
    {
        XmlFileWriter xmlWriter = new XmlFileWriter();
        xmlWriter.setIndent(true);
        xmlWriter.setIndentWidth(4);

        File logfile = this.folder.newFile();
        String logfileStr = logfile.getCanonicalPath();
        try
        {
            xmlWriter.openFile(logfileStr);

            xmlWriter.startPrinting("DATA", "root_element");
            xmlWriter.writeStartTag("One");
            xmlWriter.printAttribute("attribute_one", "first_attribute");
            xmlWriter.writeContent("First Element");
            xmlWriter.writeEndTag("One");
            xmlWriter.writeSimpleTag("Two", "Second Element");
            xmlWriter.printObject("Three", Timestamp.valueOf("2001-01-03 12:30:00.0"));

            xmlWriter.stopPrinting("DATA");
        }
        finally
        {
            xmlWriter.close();
        }

        Assert.assertTrue(logfile.exists());
        Document doc = this.createXmlDocument(logfile);
        NodeList nodeList = doc.getElementsByTagName("DATA");
        Assert.assertEquals("DATA", nodeList.item(0).getNodeName());

        NodeList nodeList1 = doc.getElementsByTagName("One");
        Assert.assertEquals("One", nodeList1.item(0).getNodeName());
        Assert.assertTrue(nodeList1.item(0).hasAttributes());
        Assert.assertEquals("First Element", nodeList1.item(0).getTextContent());

        NodeList nodeList2 = doc.getElementsByTagName("Two");
        Assert.assertEquals("Two", nodeList2.item(0).getNodeName());
        Assert.assertEquals("Second Element", nodeList2.item(0).getTextContent());

        NodeList nodeList3 = doc.getElementsByTagName("Three");
        Assert.assertEquals("Three", nodeList3.item(0).getNodeName());
        Assert.assertEquals("2001-01-03 12:30:00", nodeList3.item(0).getAttributes().getNamedItem("value").getTextContent());
    }

    @Test
    public void testPrintCollection() throws Exception
    {
        File logfile = this.folder.newFile();
        String logfileStr = logfile.getCanonicalPath();

        XmlFileWriter xmlWriter = new XmlFileWriter();
        xmlWriter.setIndent(true);
        try
        {
            xmlWriter.openFile(logfileStr);

            xmlWriter.startPrinting("DATA", "");
            List printableList = JrpipTestCase.newListWith("one", 2, 3.0, 0.4);
            xmlWriter.printCollection("printables", printableList);
            xmlWriter.stopPrinting("DATA");
        }
        finally
        {
            xmlWriter.close();
        }
        Document doc = this.createXmlDocument(logfile);
        NodeList nodeList1 = doc.getElementsByTagName("ArrayList");
        Assert.assertEquals("printables", nodeList1.item(0).getAttributes().getNamedItem("name").getNodeValue());
        Assert.assertEquals("one,2,3,0.4", nodeList1.item(0).getAttributes().getNamedItem("value").getNodeValue());
    }

    @Test
    public void testPrintArray() throws Exception
    {
        File logfile = this.folder.newFile();
        String logfileStr = logfile.getCanonicalPath();

        XmlFileWriter xmlWriter = new XmlFileWriter();
        xmlWriter.setIndent(true);
        try
        {
            xmlWriter.openFile(logfileStr);

            xmlWriter.startPrinting("DATA", "");
            int[] intArray = {1, 2, 3, 4, 5};
            Integer[] integerArray = {1, 2, 3, 4, 5};
            float[] floatArray = {1.111111f, 2.222222f, 3.333333f, 4.444444f, 5.555555f};

            xmlWriter.printArray("intArray", intArray);
            xmlWriter.printArray("integerArray", integerArray);
            xmlWriter.printArray("floatArray", floatArray);
            xmlWriter.stopPrinting("DATA");
        }
        finally
        {
            xmlWriter.close();
        }

        Document doc = this.createXmlDocument(logfile);
        NodeList nodeList = doc.getElementsByTagName("array");
        int counter = 0;
        for (int i = 0; i < 3; i++)
        {
            String name = nodeList.item(i).getAttributes().getNamedItem("name").getTextContent();
            String value = nodeList.item(i).getAttributes().getNamedItem("value").getTextContent();
            if ("intArray".equals(name) || "integerArray".equals(name))
            {
                Assert.assertEquals("1,2,3,4,5", value);
                counter++;
            }
            if ("floatArray".equals(name))
            {
                Assert.assertEquals("1.1111,2.2222,3.3333,4.4444,5.5556", value);
                counter++;
            }
        }
        Assert.assertEquals(3, counter);
    }

    @Test
    public void testWritingEscapeCharacters() throws Exception
    {
        File logfile = this.folder.newFile();
        String logfileStr = logfile.getCanonicalPath();

        XmlFileWriter xmlWriter = new XmlFileWriter();
        xmlWriter.setIndent(true);
        char[] escapeChars = {'<', '>', '&', '\'', '"', '\t', 127};
        try
        {
            xmlWriter.openFile(logfileStr);
            xmlWriter.writeStartTag("data");
            for (int i = 0; i < escapeChars.length; i++)
            {
                xmlWriter.writeStartTag("_" + i);
                xmlWriter.writeContent(String.valueOf(escapeChars[i]));
                xmlWriter.writeEndTag("_" + i);
            }
            xmlWriter.writeEndTag("data");
        }
        finally
        {
            xmlWriter.close();
        }

        Document doc = this.createXmlDocument(logfile);
        NodeList nodeList1 = doc.getElementsByTagName("_0");
        Assert.assertEquals("<", nodeList1.item(0).getTextContent());
        NodeList nodeList2 = doc.getElementsByTagName("_2");
        Assert.assertEquals("&", nodeList2.item(0).getTextContent());
    }

    private Document createXmlDocument(File xmlLog) throws Exception
    {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(xmlLog);
    }
}
