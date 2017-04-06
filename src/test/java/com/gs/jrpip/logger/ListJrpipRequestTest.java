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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.gs.jrpip.MockVirtualOutputStreamCreator;
import com.gs.jrpip.JrpipTestCase;
import com.gs.jrpip.client.FastServletProxyFactory;
import com.gs.jrpip.util.stream.VirtualOutputStreamFactory;
import com.gs.jrpip.util.stream.readback.RequestData;
import org.junit.Assert;

public class ListJrpipRequestTest extends JrpipTestCase
{
    private static final String JRPIP_LOG_DIR = "jrpipBinaryLogs";
    private static final String ECHO_STR = "Don't go around saying the world owes you a living. The world owes you nothing. It was here first.";

    private MockVirtualOutputStreamCreator virtualOutputStreamCreator;

    @Override
    protected void setUp() throws Exception
    {
        JrpipLogGenerator.deleteDirectory(JRPIP_LOG_DIR);
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
        JrpipLogGenerator.deleteDirectory(JRPIP_LOG_DIR);
        System.clearProperty(FastServletProxyFactory.MAX_CONNECTIONS_PER_HOST);
        System.clearProperty("jrpip.enableBinaryLogs");
        super.tearDown();
    }

    public void testSimpleListJrpipRequest() throws Exception
    {
        JrpipLogGenerator.createJrpipDumpFiles(3, "Test String", this.virtualOutputStreamCreator, this.buildEchoProxy(), 3);
        File binaryLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_DIR, ".log");
        Assert.assertNotNull(binaryLog);
        ListJrpipRequest.main(new String[]{"-f", binaryLog.getPath()});
    }

    public void testListJrpipRequestFailConstruct()
    {
        boolean exceptionThrown = false;
        try
        {
            ListJrpipRequest.main(new String[]{" "});
        }
        catch (Exception e)
        {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

    public void testMethodFilterOption() throws Exception
    {
        List<RequestData> requestDataList = this.listWithFilters(new String[]{"-method", "echo"}, 5);
        Assert.assertEquals(5, requestDataList.size());
        for (RequestData data : requestDataList)
        {
            Assert.assertEquals("echo", data.getMethodName());
        }
    }

    public void testIdFilerOption() throws Exception
    {
        List<RequestData> requestDataList = this.listWithFilters(new String[]{"-id", "1"}, 5);
        Assert.assertEquals(1, requestDataList.size());
        Assert.assertEquals(1, requestDataList.get(0).getStreamId());
    }

    public void testArgumentFilterOption() throws Exception
    {
        List<RequestData> requests = this.listWithFilters(new String[]{"-argument", ".*nothing.*"}, 5);
        int size = requests.size();
        Assert.assertEquals(5, size);
        for (RequestData request : requests)
        {
            String foundData = (String) request.getArguments()[0];
            Assert.assertEquals(ECHO_STR, foundData);
        }
    }

    public void testJrpipLogListMultiFilter() throws Exception
    {
        List<RequestData> requests = this.listWithFilters(new String[]{"-id", "1", "-method", "echo", "-argument", ".*nothing.*"}, 5);
        int count = requests.size();
        Assert.assertEquals(1, count);
    }

    private List<RequestData> listWithFilters(String[] filterOptAndValue, int totalRequestCount) throws Exception
    {
        JrpipLogGenerator.createJrpipDumpFiles(1, ECHO_STR, this.virtualOutputStreamCreator, this.buildEchoProxy(), totalRequestCount);
        File binaryLog = JrpipLogGenerator.findLogFileByExtension(JRPIP_LOG_DIR, ".log");
        List<String> arguments = newListWith(filterOptAndValue);
        arguments.add("-f");
        arguments.add(binaryLog.getPath());
        String[] args = new String[arguments.size()];
        arguments.toArray(args);
        MockListJrpipRequest listJrpipRequest = new MockListJrpipRequest(args);
        listJrpipRequest.listContent();
        return listJrpipRequest.matchedRequests;
    }

    private static final class MockListJrpipRequest extends ListJrpipRequest
    {
        private final List<RequestData> matchedRequests = new ArrayList<RequestData>();

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
}
