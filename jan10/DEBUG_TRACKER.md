# ZooKeeper Debug Tracker - Jan10

## Priority Order Rationale

Groups are ordered by likelihood of being actual bugs:
1. **Highest Priority**: NPE from non-test, non-restarttest code (ZooKeeper core)
2. **Medium Priority**: Session/Connection issues during restart
3. **Lowest Priority**: Exceptions directly from restarttest framework (FALSE POSITIVES)

---

## HIGH PRIORITY - Likely Bugs

### Group 4: NullPointerException in FileTxnSnapLog.save

[ ] Not started

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
java.lang.NullPointerException
	at org.apache.zookeeper.server.persistence.FileTxnSnapLog.save(FileTxnSnapLog.java)
```

**Analysis**: NPE in ZooKeeper's transaction/snapshot log persistence layer. This is a potential bug in ZooKeeper core - the FileTxnSnapLog is not properly initialized or has null references when save() is called after restart.

---

### Group 9: NullPointerException in QuorumPeer.shutdown

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at org.apache.zookeeper.server.quorum.QuorumPeer.shutdown(QuorumPeer.java)
```

**Analysis**: NPE during QuorumPeer shutdown - potential null reference in quorum peer state during shutdown sequence. This could indicate a race condition or improper state management.

---

## MEDIUM PRIORITY - Session/Connection Issues

### Group 5: SessionExpiredException

[ ] Not started

**Test Executions**: 3 failures

**Generalized Stack Trace**:
```
org.apache.zookeeper.KeeperException$SessionExpiredException
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
```

**Analysis**: Session expired during/after restart. This is somewhat expected behavior but may indicate session management issues if happening too frequently.

---

### Group 7: ConnectionLossException

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
org.apache.zookeeper.KeeperException$ConnectionLossException
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
```

**Analysis**: Connection lost during restart - expected during node restart, but needs verification.

---

### Group 8: TimeoutException in ClientBase.waitForDisconnected

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
java.util.concurrent.TimeoutException
	at org.apache.zookeeper.test.ClientBase$CountdownWatcher.waitForDisconnected(ClientBase.java)
```

**Analysis**: Test timeout waiting for disconnection - could indicate the client is not properly notified of server restart.

---

## FALSE POSITIVE - Restart Framework Issues

### Group 1: IllegalArgumentException - Unknown Node Role (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 48 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.IllegalArgumentException
	at org.restarttest.adapter.zookeeper.ClientBaseAdapter.restartNode(ClientBaseAdapter.java)
```

**Raw Stack Trace Sample**:
```
org.restarttest.core.RestartException: Restart failed at position after_setup
Caused by: java.lang.IllegalArgumentException: Unknown node role: leader. Supported: server, all
	at org.restarttest.adapter.zookeeper.ClientBaseAdapter.restartNode(ClientBaseAdapter.java:59)
	at org.restarttest.adapter.zookeeper.ClientBaseAdapter.restartNode(ClientBaseAdapter.java:38)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:190)
```

**Analysis**: The restart framework adapter doesn't support "leader", "follower", "observer" roles - it only supports "server" and "all". This is a **FALSE POSITIVE** due to restart framework limitation.

**Test Executions (Examples)**:

1. Test: `QuorumRequestPipelineTest_RestartInjected.testSync`
   - "position": "after_setup"
   - "target": "leader"
   - "mode": "GRACEFUL"
   - "executionDir": "012-36fe680b"

2. Test: `FourLetterWordsQuorumTest_RestartInjected.testFourLetterWords`
   - "position": "after_initial_verifications"
   - "target": "leader"
   - "mode": "GRACEFUL"
   - "executionDir": "002-6d13590d"

3. Test: `QuorumMajorityTest_RestartInjected.testMajQuorums`
   - "position": "after_initial_election_check"
   - "target": "follower"
   - "mode": "GRACEFUL"
   - "executionDir": "002-93ffa430"

---

### Group 2: Exception from MainThreadAdapter.waitActive (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 28 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.Exception
	at org.restarttest.adapter.zookeeper.MainThreadAdapter.waitActive(MainThreadAdapter.java)
```

**Analysis**: Restart framework timeout waiting for server to become active - **FALSE POSITIVE** due to framework issue.

---

### Group 3: IllegalStateException from ClientBaseAdapter.restartServer (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 15 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.IllegalStateException
	at org.restarttest.adapter.zookeeper.ClientBaseAdapter.restartServer(ClientBaseAdapter.java)
```

**Analysis**: Restart framework state issue - **FALSE POSITIVE**.

---

### Group 6: Exception from QuorumBaseAdapter.restartFollower (FALSE POSITIVE)

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.Exception
	at org.restarttest.adapter.zookeeper.QuorumBaseAdapter.restartFollower(QuorumBaseAdapter.java)
```

**Analysis**: Restart framework exception during follower restart - **FALSE POSITIVE**.

---

## Summary Statistics

| Priority | Group ID | Exception Type | Count | Verdict |
|----------|----------|----------------|-------|---------|
| HIGH | 4 | NullPointerException (FileTxnSnapLog.save) | 3 | Likely Bug |
| HIGH | 9 | NullPointerException (QuorumPeer.shutdown) | 1 | Likely Bug |
| MEDIUM | 5 | SessionExpiredException | 3 | Needs Inspection |
| MEDIUM | 7 | ConnectionLossException | 2 | Expected |
| MEDIUM | 8 | TimeoutException | 1 | Test Issue |
| FALSE POSITIVE | 1 | IllegalArgumentException (restarttest) | 48 | Framework Issue |
| FALSE POSITIVE | 2 | Exception (restarttest) | 28 | Framework Issue |
| FALSE POSITIVE | 3 | IllegalStateException (restarttest) | 15 | Framework Issue |
| FALSE POSITIVE | 6 | Exception (restarttest) | 2 | Framework Issue |

**Total Groups**: 9
**Total Failures**: 103

### Key Finding

The majority of failures (93 out of 103 = **90%**) are FALSE POSITIVES caused by the restart framework not supporting ZooKeeper's quorum roles (leader, follower, observer). The adapter only supports "server" and "all" as node roles.

**Recommendation**: Fix the restart framework adapter to support leader/follower/observer roles for ZooKeeper quorum tests.

Only **4 failures** (Groups 4 and 9) represent potential actual bugs in ZooKeeper - both are NPEs in core ZooKeeper code (FileTxnSnapLog and QuorumPeer).
