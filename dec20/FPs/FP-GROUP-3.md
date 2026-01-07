# FP-GROUP-3: Oracle Configuration Not Preserved During Restart

## Summary
**Classification:** False Positive (FP)
**Root Cause:** The restart adapter uses `QuorumBase.setupServer()` which does not preserve Oracle configuration when recreating servers.

## Failure Details
- **Exception:** `RestartException: Restart failed at position after_create_node`
- **Caused by:** `java.lang.Exception: Server failed to start at 127.0.0.1:XXXXX`
- **Source:** `QuorumBaseAdapter.waitActive(QuorumBaseAdapter.java:112)`

## Affected Tests
- `QuorumRequestPipelineTest_RestartInjected.testSync`
- `QuorumRequestPipelineTest_RestartInjected.testSynchronousSync`
- And other tests in this class with restart at position `after_create_node`

## Root Cause Analysis

### The Problem
When a server is restarted using the `QuorumBaseAdapter`, it calls `QuorumBase.setupServer(index)` to recreate the QuorumPeer. However, the `setupServer()` method does NOT preserve the Oracle configuration that was used during initial setup.

### Evidence from Logs
After restart, the server repeatedly logs:
```
Oracle indicates not to follow
```

The restarted server stays in `LOOKING` state indefinitely because:
1. It cannot join the existing quorum
2. Its Oracle configuration is missing (was not passed during recreation)
3. The existing leader election is based on Oracle-enabled peers

### Code Analysis

**Initial Setup with Oracle (QuorumBase.java lines 218-234):**
```java
// When withOracle=true, servers are created WITH oracle path:
s1 = new QuorumPeer(peers, s1dir, s1dir, portClient1, 3, 1, tickTime,
    initLimit, syncLimit, connectToLearnerMasterLimit,
    oracleDir.getAbsolutePath() + oraclePath_0 + mastership);  // Oracle parameter included!
```

**Restart Setup (QuorumBase.java lines 446-470):**
```java
// setupServer() method does NOT include oracle path:
public void setupServer(int i) throws IOException {
    // ...
    switch (i) {
    case 1:
        s1 = new QuorumPeer(peers, s1dir, s1dir, portClient1, 3, 1,
            tickTime, initLimit, syncLimit, connectToLearnerMasterLimit);
        // NO oracle parameter!
        break;
    // ... similar for other servers
    }
}
```

**Adapter calling setupServer (QuorumBaseAdapter.java line 263):**
```java
// Recreate and restart
cluster.setupServer(index + 1); // setupServer uses 1-5
```

### Why This is a False Positive

1. **Not a ZooKeeper Source Code Bug:** The ZooKeeper server code works correctly. The "Oracle indicates not to follow" behavior is the expected result when Oracle configuration is inconsistent between peers.

2. **Restart Infrastructure Issue:** The `QuorumBase.setupServer()` method was designed for simple setup scenarios and was never intended to be used for restart when Oracle is enabled.

3. **Improper Restart Position:** The restart framework injects a restart at a position where the Oracle-enabled test cannot properly handle server recreation.

## Resolution

This is not a bug in ZooKeeper. The restart adapter needs to be enhanced to:
1. Detect if Oracle configuration was used in the original setup
2. Store and reuse the Oracle path when recreating servers via `setupServer()`

Alternatively, the test infrastructure (`QuorumBase.setupServer()`) could be enhanced to support Oracle configuration, but this would require significant changes to the test class.

## Conclusion

This failure group represents a limitation in the restart testing framework's compatibility with Oracle-enabled quorum tests, not a bug in ZooKeeper's production code.
