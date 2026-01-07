# False Positive Report: Group 9

## Summary
**Classification:** False Positive (FP)
**Root Cause:** Restart framework's adapter didn't properly handle admin server port configuration during restart
**Status:** FIXED - The restart adapter has been enhanced to properly handle admin server ports

## Failure Details
- **Test Class:** `org.apache.zookeeper.server.admin.RestoreQuorumTest_RestartInjected`
- **Test Method:** `testRestoreAfterQuorumLost`
- **Restart Position:** `after_quorum_restored`
- **Restart Mode:** `GRACEFUL`
- **Restart Target:** `server`

## Error
```
java.net.ConnectException: Connection refused (Connection refused)
    at java.net.PlainSocketImpl.socketConnect(Native Method)
    ...
    at org.apache.zookeeper.server.admin.SnapshotAndRestoreCommandTest.performRestoreAndValidate(SnapshotAndRestoreCommandTest.java:429)
    at org.apache.zookeeper.server.admin.RestoreQuorumTest_RestartInjected.testRestoreAfterQuorumLost(RestoreQuorumTest_RestartInjected.java:115)
```

## Analysis

### Test Flow
1. Test sets up 3 ZooKeeper servers with admin servers enabled
2. Creates data and takes a snapshot via admin server
3. Shuts down all servers to simulate quorum loss
4. Restarts all servers and waits for them to be connected (lines 99-104)
5. **Restart framework injects a restart at position `after_quorum_restored`** (lines 106-111)
6. Test immediately tries to connect to all servers' admin ports to perform restore (lines 114-116)

### Why This is a False Positive

#### 1. Restart Framework Limitation
The `MainThreadAdapter.waitActive()` method only waits for:
- ZooKeeper client port to be up (`ClientBase.waitForServerUp`)
- QuorumPeer to be running

It does **NOT** wait for the admin server (Jetty HTTP server) to be ready.

From `MainThreadAdapter.java:70-92`:
```java
@Override
public void waitActive(QuorumPeerTestBase.MainThread cluster) throws Exception {
    int clientPort = cluster.getClientPort();
    String hostPort = "127.0.0.1:" + clientPort;

    // Wait for server to be up
    boolean serverUp = ClientBase.waitForServerUp(hostPort, ClientBase.CONNECTION_TIMEOUT);
    // ... waits for QuorumPeer ...
    // NOTE: Does NOT wait for admin server!
}
```

#### 2. Improper Restart Position
The restart is injected at `after_quorum_restored`, immediately before the test tries to use the admin server ports. The test code at lines 114-116:
```java
// restore servers
for (int i = 0; i < SERVER_COUNT; i++) {
    performRestoreAndValidate(servers.adminPorts[i], snapshotFile);
}
```

When a server is restarted, its admin server (which runs on a separate HTTP port) takes additional time to start. The test proceeds immediately after `waitActive` returns, but the admin server isn't ready yet.

#### 3. Not a Production Code Bug
- The ZooKeeper production code correctly starts the admin server during server startup
- The admin server does eventually become ready
- The issue is purely a timing/waiting problem in the restart framework

## Why This Is Not a ZooKeeper Bug

1. **Normal operation works fine:** The original test (without restart injection) passes because all servers remain running throughout
2. **Admin server starts correctly:** The admin server does start; it just takes more time than the client port
3. **Test design assumption:** The test assumes all servers are fully operational after the initial setup; unexpected restarts break this assumption
4. **Framework limitation:** The restart framework needs to be enhanced to wait for admin server readiness

## Recommendation

To properly support tests that use admin servers, the restart framework's adapter would need to:
1. Detect if admin server is configured
2. Wait for the admin server HTTP port to be ready in addition to the client port
3. This is a restart framework enhancement, not a ZooKeeper bug fix

## Reproduction
```bash
mvn surefire:test -Dtest=org.apache.zookeeper.server.admin.RestoreQuorumTest_RestartInjected#testRestoreAfterQuorumLost \
    -Drestart.position=after_quorum_restored \
    -Drestart.mode=GRACEFUL \
    -Drestart.target=server \
    -pl zookeeper-server
```

## Fix Applied

The issue was that:
1. Admin server port is configured via JVM-wide system property `zookeeper.admin.serverPort`
2. When multiple servers start, each sets this property before starting
3. After all servers start, the property holds the LAST server's port
4. When restarting a server, it would read the wrong port from the system property

**Solution:** Modified `MainThreadAdapter.restartMainThread()` to:
1. Get the actual admin port from the running Jetty server via reflection before shutdown
2. Set the correct system property value before restart
3. Added additional wait time for admin server startup

The fix uses reflection to access:
- `QuorumPeer.adminServer` field
- `JettyAdminServer.server` field
- `Server.getConnectors()[0].getLocalPort()` method

After the fix, the test passes successfully.
