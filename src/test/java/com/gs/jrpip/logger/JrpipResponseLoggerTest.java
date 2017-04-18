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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.gs.jrpip.MockVirtualOutputStreamCreator;
import com.gs.jrpip.JrpipTestCase;
import com.gs.jrpip.client.FastServletProxyFactory;
import com.gs.jrpip.util.stream.VirtualOutputStreamFactory;
import com.gs.jrpip.util.stream.readback.RequestData;
import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class JrpipResponseLoggerTest extends JrpipTestCase
{
    private static final String JRPIP_LOG_BIN_DIR = "jrpipBinaryLogs";
    private MockVirtualOutputStreamCreator virtualOutputStreamCreator;

    @Override
    protected void setUp() throws Exception
    {
        JrpipLogGenerator.deleteDirectory(JRPIP_LOG_BIN_DIR);
        this.virtualOutputStreamCreator = new MockVirtualOutputStreamCreator();
        VirtualOutputStreamFactory.setOutputStreamCreator(this.virtualOutputStreamCreator);
        System.setProperty("jrpip.enableBinaryLogs", "true");
        System.setProperty(FastServletProxyFactory.MAX_CONNECTIONS_PER_HOST, "50");
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception
    {
        VirtualOutputStreamFactory.setOutputStreamCreator(VirtualOutputStreamFactory.DEFAULT_CREATOR);
        JrpipLogGenerator.deleteDirectory(JRPIP_LOG_BIN_DIR);
        System.clearProperty(FastServletProxyFactory.MAX_CONNECTIONS_PER_HOST);
        System.clearProperty("jrpip.enableBinaryLogs");
        super.tearDown();
    }

    public void testWithSimpleClass() throws Exception
    {
        ClassD classD = new ClassD("Class_D");
        this.convertToXmlWith(classD);

        File xmlLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".xml");
        Assert.assertTrue(xmlLog.exists());

        Document doc = this.createXmlDocument(xmlLog);
        NodeList nList = doc.getElementsByTagName("ClassD");
        Assert.assertEquals(1, nList.getLength());
    }

    public void testJrpipResponseLoggerWithDoubleList() throws Exception
    {
        List<List<String>> outerList = new ArrayList();
        List<String> innerList = newListWith("1", "2", "3", "4");
        outerList.add(innerList);

        JrpipLogGenerator.createJrpipDumpFiles(1, outerList, this.virtualOutputStreamCreator, this.buildEchoProxy(), 1);
        File binaryLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".log");
        Assert.assertNotNull(binaryLog);

        ListJrpipRequest.main(new String[]{"-f", binaryLog.getPath()});
        JrpipResponseLogger.main(new String[]{"-f", binaryLog.getPath(), "-id", "1"});
        File xmlLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".xml");
        Assert.assertTrue(xmlLog.exists());
    }

    public void testWithMap() throws Exception
    {
        Map<String, List<String>> aMap = new HashMap();
        aMap.put("key1", newListWith("value1-1", "value1-2", "value1-3", "value1-4", "value1-5"));
        aMap.put("key2", newListWith("value2-1", "value2-2", "value2-3", "value2-4", "value2-5"));
        this.convertToXmlWith(aMap);

        File xmlLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".xml");
        Assert.assertTrue(xmlLog.exists());

        Document doc = this.createXmlDocument(xmlLog);
        NodeList nList = doc.getElementsByTagName("HashMap");
        Assert.assertEquals(1, nList.getLength());
        NodeList keyList = doc.getElementsByTagName("key");
        Assert.assertEquals(2, keyList.getLength());
        NodeList valueList = doc.getElementsByTagName("value");
        Assert.assertEquals(2, valueList.getLength());
    }

    public void testWithMapOfLists() throws Exception
    {
        List<ClassD> classDList = newListWith(new ClassD("classD-1"), new ClassD("classD-2"));
        Map<String, List<ClassD>> map = new HashMap();
        map.put("one", classDList);
        this.convertToXmlWith(map);

        File xmlLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".xml");
        Assert.assertTrue(xmlLog.exists());

        Document doc = this.createXmlDocument(xmlLog);
        NodeList nList1 = doc.getElementsByTagName("key");
        Assert.assertEquals(1, nList1.getLength());

        NodeList nList2 = doc.getElementsByTagName("ClassD");
        Assert.assertEquals(2, nList2.getLength());

        NodeList nList3 = doc.getElementsByTagName("String");
        Assert.assertEquals(1, nList3.getLength());
    }

    public void testWithNulls() throws Exception
    {
        List<ClassC> cList = new ArrayList();
        cList.add(new ClassC(null, null, newListWith(1, 2, 3, 4), null, null));

        ClassBExtendsA bExtendsA = new ClassBExtendsA("B_extending_A", cList);
        this.convertToXmlWith(bExtendsA);

        File xmlLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".xml");
        Assert.assertTrue(xmlLog.exists());
    }

    public void testWithInheritance() throws Exception
    {
        List<ClassC> cList = new ArrayList();
        Map<String, List<ClassD>> map = new HashMap();
        map.put("one", newListWith(new ClassD("d-one")));

        cList.add(new ClassC("classC",
                new int[]{1, 2, 3, 4},
                newListWith(1, 2, 3, 4),
                newListWith(1.1, 2.1, 3.1, 4.1),
                map));

        Map<String, List<ClassD>> map2 = new HashMap();
        map.put("two", newListWith(new ClassD("d-two")));
        cList.add(new ClassC("classC",
                new int[]{5, 6, 7, 8},
                newListWith(5, 6, 7, 8),
                newListWith(1.2, 2.2, 3.2, 4.2),
                map2));

        ClassBExtendsA bExtendsA = new ClassBExtendsA("B_extending_A", cList);
        this.convertToXmlWith(bExtendsA);

        File xmlLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".xml");
        Assert.assertTrue(xmlLog.exists());
    }

    public void testWithMultipleIdMatch() throws Exception
    {
        List<String> echoStrings = newListWith(
                "This is one time we don't wait for the Batphone. ",
                "The Batmobile, we'll have to have it fumagated.",
                "A peanut butter and water crest sandwich and a glass of milk would have been sufficient enough, Alfred."
        );
        int echoCount = 5;
        JrpipLogGenerator.createJrpipDumpFiles(1, echoStrings, this.virtualOutputStreamCreator, this.buildEchoProxy(), echoCount);
        List<RequestData> requests = this.listAllJrpipRequests();


        List<Integer> streamIds = new ArrayList<Integer>();
        for(RequestData rd: requests)
        {
            streamIds.add(rd.getStreamId());
        }
        Assert.assertEquals(echoCount, streamIds.size());

        File binaryLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".log");
        Assert.assertNotNull(binaryLog);

        String idString = "";
        for(Integer i: streamIds)
        {
            if (idString.length() > 0)
            {
                idString = idString + ",";
            }
            idString = idString + i;
        }
        ListJrpipRequest.main(new String[]{"-f", binaryLog.getPath()});
        JrpipResponseLogger.main(new String[]{"-f", binaryLog.getPath(), "-id", idString});
        File xmlLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".xml");
        Assert.assertTrue(xmlLog.exists());
    }

    public void testPassThruDataHandler()
    {
        boolean exceptionThrown = false;
        try
        {
            MockCustomizedResponseHandler passThruDataHandler = new MockCustomizedResponseHandler();
            passThruDataHandler.writeObject(null, null);
        }
        catch (Exception e)
        {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

    private List<RequestData> listAllJrpipRequests() throws Exception
    {
        File binaryLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_BIN_DIR, ".log");
        String[] args = new String[2];
        args[0] = "-f";
        args[1] = binaryLog.getPath();
        MockListJrpipRequest listJrpipRequest = new MockListJrpipRequest(args);
        listJrpipRequest.listContent();
        return listJrpipRequest.matchedRequests;
    }

    private Document createXmlDocument(File xmlLog) throws Exception
    {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(xmlLog);
    }

    private void convertToXmlWith(Object dataObject) throws Exception
    {
        ResponseParserWriter responseParserWriter = null;
        try
        {
            responseParserWriter = new ResponseParserWriter();
            responseParserWriter.startDocument(JRPIP_LOG_BIN_DIR + "/testResponseParserWriter.xml");
            responseParserWriter.convertDataToXml("", dataObject, null, new MockCustomizedResponseHandler());
        }
        finally
        {
            if (responseParserWriter != null)
            {
                responseParserWriter.endDocument();
            }
        }
    }

    private static final class MockListJrpipRequest extends ListJrpipRequest
    {
        private final List<RequestData> matchedRequests = new ArrayList();

        private MockListJrpipRequest(String[] arguments)
        {
            super(arguments);
        }

        @Override
        protected void writeResult(RequestDataDetailWriter writer, Iterator<RequestData> matchedRequests)
        {
            while (matchedRequests.hasNext())
            {
                RequestData requestData = matchedRequests.next();
                this.matchedRequests.add(requestData);
            }
        }
    }

    // ====================================
    // ====================================

    public static class ClassA
    {
        private final String aString;
        private final Date aDate;
        private final Timestamp aTimeStamp;
        private final List<String> aStringList;
        private final List<ClassD> aDList;

        public ClassA(
                String aString,
                Date aDate,
                Timestamp aTimeStamp,
                List<String> aStringList,
                List<ClassD> aDList)
        {
            this.aString = aString;
            this.aDate = aDate;
            this.aTimeStamp = aTimeStamp;
            this.aStringList = aStringList;
            this.aDList = aDList;
        }
    }

    public static class ClassBExtendsA extends ClassA
    {
        private final String bString;
        private final List<ClassC> bListOfC;

        public ClassBExtendsA(String name, List<ClassC> classCList)
        {
            super("aString", new Date(System.currentTimeMillis()),
                    new Timestamp(System.currentTimeMillis()), newListWith("z", "y", "x", "w", "v"),
                    newListWith(new ClassD("dString1"), new ClassD("dString2"), new ClassD("dString3")));

            this.bString = name;
            this.bListOfC = classCList;
        }
    }

    public static class ClassD
    {
        private final String dString;
        private final int dInt;

        public ClassD(String name)
        {
            this.dString = name;
            this.dInt = 3;
        }
    }

    public static class ClassC
    {
        private final String cString;
        private final int[] cIntArray;
        private final List<Integer> cIntList;
        private final List<Double> cDoubleList;
        private final Map<String, List<ClassD>> cMap;

        public ClassC(
                String cString,
                int[] cIntArray,
                List<Integer> cIntList,
                List<Double> cDoubleList,
                Map<String, List<ClassD>> cMap)
        {
            this.cString = cString;
            this.cIntArray = cIntArray;
            this.cIntList = cIntList;
            this.cDoubleList = cDoubleList;
            this.cMap = cMap;
        }
    }
}
