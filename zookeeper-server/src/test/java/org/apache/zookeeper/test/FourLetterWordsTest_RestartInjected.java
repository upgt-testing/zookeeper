/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.test;

import static org.apache.zookeeper.client.FourLetterWordMain.send4LetterWord;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;
import org.apache.zookeeper.TestableZooKeeper;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.common.IOUtils;
import org.apache.zookeeper.common.X509Exception.SSLContextException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FourLetterWordsTest_RestartInjected extends ClientBase {

    protected static final Logger LOG = LoggerFactory.getLogger(FourLetterWordsTest_RestartInjected.class);

    /** Test the various four letter words */
    @Test
    @Timeout(value = 30)
    public void testFourLetterWords() throws Exception {
        verify("ruok", "imok");
        verify("envi", "java.version");
        verify("conf", "clientPort");
        verify("stat", "Outstanding");
        verify("srvr", "Outstanding");
        verify("cons", "queued");
        verify("dump", "Session");
        verify("wchs", "watches");
        verify("wchp", "");
        verify("wchc", "");

        verify("srst", "reset");
        verify("crst", "reset");

        verify("stat", "Outstanding");
        verify("srvr", "Outstanding");
        verify("cons", "queued");
        verify("gtmk", "306");
        verify("isro", "rw");

        RestartFramework.at("after_initial_verify_commands")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        TestableZooKeeper zk = createClient();
        String sid = getHexSessionId(zk.getSessionId());

        RestartFramework.at("after_create_client")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        verify("stat", "queued");
        verify("srvr", "Outstanding");
        verify("cons", sid);
        verify("dump", sid);
        verify("dirs", "size");

        zk.getData("/", true, null);

        RestartFramework.at("after_get_data_with_watch")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        verify("stat", "queued");
        verify("srvr", "Outstanding");
        verify("cons", sid);
        verify("dump", sid);

        verify("wchs", "watching 1");
        verify("wchp", sid);
        verify("wchc", sid);
        verify("dirs", "size");
        zk.close();

        RestartFramework.at("after_close_client")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        verify("ruok", "imok");
        verify("envi", "java.version");
        verify("conf", "clientPort");
        verify("stat", "Outstanding");
        verify("srvr", "Outstanding");
        verify("cons", "queued");
        verify("dump", "Session");
        verify("wchs", "watch");
        verify("wchp", "");
        verify("wchc", "");

        verify("srst", "reset");
        verify("crst", "reset");

        verify("stat", "Outstanding");
        verify("srvr", "Outstanding");
        verify("cons", "queued");
        verify("mntr", "zk_server_state\tstandalone");
        verify("mntr", "num_alive_connections");
        verify("stat", "Connections");
        verify("srvr", "Connections");
        verify("dirs", "size");
    }

    private String sendRequest(String cmd) throws IOException, SSLContextException {
        HostPort hpobj = ClientBase.parseHostPortList(hostPort).get(0);
        return send4LetterWord(hpobj.host, hpobj.port, cmd);
    }
    private String sendRequest(String cmd, int timeout) throws IOException, SSLContextException {
        HostPort hpobj = ClientBase.parseHostPortList(hostPort).get(0);
        return send4LetterWord(hpobj.host, hpobj.port, cmd, false, timeout);
    }

    private void verify(String cmd, String expected) throws IOException, SSLContextException {
        String resp = sendRequest(cmd);
        LOG.info("cmd {} expected {} got {}", cmd, expected, resp);
        assertTrue(resp.contains(expected));
    }

    @Test
    @Timeout(value = 30)
    public void testValidateStatOutput() throws Exception {
        ZooKeeper zk1 = createClient();
        ZooKeeper zk2 = createClient();

        RestartFramework.at("after_create_two_clients")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        String resp = sendRequest("stat");
        BufferedReader in = new BufferedReader(new StringReader(resp));

        String line;
        // first line should be version info
        line = in.readLine();
        assertTrue(Pattern.matches("^.*\\s\\d+\\.\\d+\\.\\d+-.*$", line));
        assertTrue(Pattern.matches("^Clients:$", in.readLine()));

        int count = 0;
        while ((line = in.readLine()).length() > 0) {
            count++;
            assertTrue(Pattern.matches("^ /.*:\\d+\\[\\d+\\]\\(queued=\\d+,recved=\\d+,sent=\\d+\\)$", line));
        }
        // ensure at least the two clients we created are accounted for
        assertTrue(count >= 2);

        line = in.readLine();
        assertTrue(Pattern.matches("^Latency min/avg/max: \\d+/-?[0-9]*.?[0-9]*/\\d+$", line));
        line = in.readLine();
        assertTrue(Pattern.matches("^Received: \\d+$", line));
        line = in.readLine();
        assertTrue(Pattern.matches("^Sent: \\d+$", line));
        line = in.readLine();
        assertTrue(Pattern.matches("^Connections: \\d+$", line));
        line = in.readLine();
        assertTrue(Pattern.matches("^Outstanding: \\d+$", line));
        line = in.readLine();
        assertTrue(Pattern.matches("^Zxid: 0x[\\da-fA-F]+$", line));
        line = in.readLine();
        assertTrue(Pattern.matches("^Mode: .*$", line));
        line = in.readLine();
        assertTrue(Pattern.matches("^Node count: \\d+$", line));

        zk1.close();
        zk2.close();
    }

    @Test
    @Timeout(value = 30)
    public void testValidateConsOutput() throws Exception {
        ZooKeeper zk1 = createClient();
        ZooKeeper zk2 = createClient();

        RestartFramework.at("after_create_two_clients_cons")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        String resp = sendRequest("cons");
        BufferedReader in = new BufferedReader(new StringReader(resp));

        String line;
        int count = 0;
        while ((line = in.readLine()) != null && line.length() > 0) {
            count++;
            assertTrue(Pattern.matches("^ /.*:\\d+\\[\\d+\\]\\(queued=\\d+,recved=\\d+,sent=\\d+.*\\)$", line), line);
        }
        // ensure at least the two clients we created are accounted for
        assertTrue(count >= 2);

        zk1.close();
        zk2.close();
    }

    @Test
    @Timeout(value = 60)
    public void testValidateSocketTimeout() throws Exception {
        /**
         * testing positive scenario that even with timeout parameter the
         * functionality works fine
         */
        String resp = sendRequest("isro", 2000);

        RestartFramework.at("after_send_request_with_timeout")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertTrue(resp.contains("rw"));
    }

    @Test
    @Timeout(value = 30)
    public void testSetTraceMask() throws Exception {
        String gtmkResp = sendRequest("gtmk");
        assertNotNull(gtmkResp);
        gtmkResp = gtmkResp.trim();
        assertFalse(gtmkResp.isEmpty());
        long formerMask = Long.valueOf(gtmkResp);

        RestartFramework.at("after_get_trace_mask")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        try {
            verify(buildSetTraceMaskRequest(0), "0");
            verify("gtmk", "0");

            RestartFramework.at("after_set_trace_mask_to_zero")
                .on(this)
                .restart("server")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
        } finally {
            // Restore former value.
            sendRequest(buildSetTraceMaskRequest(formerMask));
        }
    }

    /**
     * Builds a SetTraceMask request to be sent to the server, consisting of
     * "stmk" followed by the 8-byte long representation of the trace mask.
     *
     * @param mask trace mask to set
     * @return built request
     * @throws IOException if there is an I/O error
     */
    private String buildSetTraceMaskRequest(long mask) throws IOException {
        ByteArrayOutputStream baos = null;
        DataOutputStream dos = null;
        try {
            baos = new ByteArrayOutputStream();
            dos = new DataOutputStream(baos);
            dos.writeBytes("stmk");
            dos.writeLong(mask);
        } finally {
            IOUtils.closeStream(dos);
            IOUtils.closeStream(baos);
        }
        return new String(baos.toByteArray());
    }

}
