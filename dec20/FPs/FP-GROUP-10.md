# FP-GROUP-10: TimeoutException in waitForDisconnected - False Positive

## Summary
This failure is a **False Positive** caused by improper restart position. The restart at `after_auth_failure` disrupts the expected client disconnection behavior by triggering ZooKeeper client reconnection.

## Failure Information
- **Test Class:** `org.apache.zookeeper.test.SaslAuthRequiredFailNoSASLTest_RestartInjected`
- **Test Method:** `testClientOpWithoutSASLConfigured`
- **Restart Position:** `after_auth_failure`
- **Restart Mode:** `GRACEFUL`
- **Restart Target:** `server`

## Exception
```
java.util.concurrent.TimeoutException: Did not disconnect: connected(true), syncConnected(true), readOnlyConnected(false)
    at org.apache.zookeeper.test.ClientBase$CountdownWatcher.waitForDisconnected(ClientBase.java:178)
    at org.apache.zookeeper.test.SaslAuthRequiredFailNoSASLTest_RestartInjected.testClientOpWithoutSASLConfigured(SaslAuthRequiredFailNoSASLTest_RestartInjected.java:71)
```

## Root Cause Analysis

### Original Test Logic
The original test (`SaslAuthRequiredFailNoSASLTest.java`) validates that:
1. Server is configured to require SASL authentication
2. Client connects WITHOUT SASL configured
3. Client tries to create a node and gets `SESSIONCLOSEDREQUIRESASLAUTH` exception
4. After the exception, the client should eventually disconnect

### Test Code Flow
```java
// SaslAuthRequiredFailNoSASLTest_RestartInjected.java
@Test
public void testClientOpWithoutSASLConfigured() throws Exception {
    ZooKeeper zk = null;
    CountdownWatcher watcher = new CountdownWatcher();
    try {
        zk = createClient(watcher);
        zk.create("/foo", null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        fail("Client is not configured with SASL authentication...");
    } catch (KeeperException e) {
        assertTrue(e.code() == KeeperException.Code.SESSIONCLOSEDREQUIRESASLAUTH);

        // Restart point - THIS IS THE PROBLEM
        RestartFramework.at("after_auth_failure")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // Test expects client to disconnect after SASL auth failure
        watcher.waitForDisconnected(SaslTestUtil.CLIENT_DISCONNECT_TIMEOUT);  // Line 71 - FAILS
    } finally {
        if (zk != null) {
            zk.close();
        }
    }
}
```

### Why the Failure Occurs
1. Client receives `SESSIONCLOSEDREQUIRESASLAUTH` exception
2. At `after_auth_failure` position, the restart framework performs a graceful server restart
3. During the restart:
   - Server is stopped
   - Client detects disconnection
   - Server is restarted
   - **ZooKeeper client automatically reconnects to the new server** (this is normal/expected ZK behavior)
4. After the restart completes, the client is back in `connected` state
5. The test then calls `watcher.waitForDisconnected()` expecting the client to be disconnected
6. Since the client is now connected (due to reconnection), the call times out

### Why This is NOT a Bug
1. **Expected ZK Client Behavior:** ZooKeeper clients are designed to maintain sessions and reconnect after server restarts. This is a core feature of ZK's reliability model.
2. **No Source Code Issue:** The ZooKeeper production code is behaving correctly.
3. **Restart Position Issue:** The restart at `after_auth_failure` is problematic because:
   - It happens right after receiving the SASL auth failure
   - The client was in the process of closing its connection
   - The restart interrupts this process and triggers reconnection
4. **Test Assertion Invalidated:** The test's assertion about disconnection is only valid when there's no server restart. The restart changes the expected behavior.

## Verification
The `waitForDisconnected` method in `ClientBase.java`:
```java
public synchronized void waitForDisconnected(long timeout) throws InterruptedException, TimeoutException {
    long expire = Time.currentElapsedTime() + timeout;
    long left = timeout;
    while (connected && left > 0) {
        wait(left);
        left = expire - Time.currentElapsedTime();
    }
    if (connected) {
        throw new TimeoutException("Did not disconnect: " + connectionDescription());
    }
}
```

The error message `connected(true), syncConnected(true)` confirms the client reconnected after the server restart.

## Conclusion
This is a **False Positive** due to improper restart position. The restart at `after_auth_failure`:
- Interferes with the expected client behavior after SASL auth failure
- Triggers ZooKeeper client reconnection (which is correct behavior)
- Invalidates the test's assumption that the client should be disconnected

**Classification:** FP (False Positive) - Improper restart position
