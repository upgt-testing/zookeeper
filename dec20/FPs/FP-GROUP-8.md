# FP-GROUP-8: SessionExpiredException with Local Sessions During Leader Election

## Classification: False Positive (FP)

## Summary
The `SessionExpiredException` observed in this failure group is **expected behavior** when using local sessions in ZooKeeper. Local sessions are designed to be non-replicated, in-memory sessions that do NOT survive server state transitions such as leader re-election.

## Failure Details

### Exception
```
org.apache.zookeeper.KeeperException$SessionExpiredException: KeeperErrorCode = Session expired for /ephemeralcreatemultiop
    at org.apache.zookeeper.KeeperException.create(KeeperException.java:133)
    at org.apache.zookeeper.ZooKeeper.create(ZooKeeper.java:1347)
    at org.apache.zookeeper.server.MultiOpSessionUpgradeTest_RestartInjected.ephemeralCreateMultiOpTest
```

### Test Configuration
- **Test Class:** `org.apache.zookeeper.server.MultiOpSessionUpgradeTest_RestartInjected`
- **Test Method:** `ephemeralCreateMultiOpTest`
- **Restart Position:** `after_create_client`
- **Restart Mode:** `GRACEFUL`
- **Restart Target:** `leader`
- **Local Sessions:** Enabled (`localSessionsEnabled = true`)
- **Session Upgrading:** Enabled (`localSessionsUpgradingEnabled = true`)

## Root Cause Analysis

### Execution Flow
1. **Client Connection:** Client connects to a follower (server 3, port 11235)
2. **Session Creation:** A LOCAL session is created on server 3 (in-memory only)
3. **Leader Restart:** Leader (server 5, port 11237) is restarted via the restart framework
4. **Leader Election Triggered:** All followers lose connection to the leader and enter LOOKING state
5. **Follower Shutdown:** Server 3 (the follower the client is connected to) shuts down its ZooKeeperServer as part of re-election
6. **Local Sessions Lost:** During `ZooKeeperServer.shutdown()`, the session tracker is shutdown, clearing ALL local sessions
7. **Server Re-Syncs:** Server 3 re-joins the quorum after election, but the local session is gone
8. **SessionExpiredException:** When the client tries to create a node, the session no longer exists

### Key Code Paths

**QuorumPeer.java** - When follower loses leader connection:
```java
case FOLLOWING:
    try {
        setFollower(makeFollower(logFactory));
        follower.followLeader();  // Throws when leader disconnects
    } finally {
        follower.shutdown();      // Shuts down ZooKeeperServer
        setFollower(null);
        updateServerState();      // Sets state to LOOKING
    }
```

**Learner.java** - Shutdown clears server state:
```java
public void shutdown() {
    // ...
    if (zk != null) {
        zk.shutdown(self.getSyncMode().equals(QuorumPeer.SyncMode.SNAP));
    }
}
```

**ZooKeeperServer.java** - Session tracker is shutdown:
```java
if (sessionTracker != null) {
    sessionTracker.shutdown();  // Clears all local sessions
}
```

## Why This Is Expected Behavior (FP)

### 1. Local Session Design Intent
Local sessions are specifically designed to:
- Be stored only in memory on the server where the client connects
- NOT be replicated to other servers in the quorum
- NOT survive server state transitions (shutdown, restart, re-election)

### 2. ZooKeeper Documentation
Local sessions are intended for:
- Read-only operations
- Scenarios where session durability is not required
- Performance optimization (avoiding global session overhead)

### 3. No Bug in Production Code
The behavior is consistent with ZooKeeper's design:
- Followers shutdown their ZooKeeperServer during leader re-election to re-sync
- Session trackers are cleared as part of this shutdown
- This ensures a clean state when re-syncing with the new leader

### 4. Correct Behavior for Global Sessions
If durability across server failures is needed:
- Use global sessions (`localSessionsEnabled = false`)
- Global sessions are replicated to the leader and survive re-election

## Debug Evidence

From the test output logs:
```
DEBUG: Client connected to server on port: 11235
DEBUG: Leader port: 11237, Leader index: 4

[Leader restart triggered]

New election. My id = 3, proposed zxid=0x0  // Server 3 goes into election

DEBUG: After restart - could not find connected server (session may be expired)
DEBUG: ZooKeeper client state: CONNECTED  // Client reconnects but session is gone
```

The client shows `CONNECTED` state after reconnection, but no server is tracking the session because:
- Server 3 (original session holder) lost the local session during re-election
- No other server knows about this local session (by design)

## Conclusion

This failure is a **False Positive** because:
1. The behavior is intentional and consistent with local session design
2. The production code is working correctly
3. The failure only occurs due to the combination of:
   - Local sessions being enabled
   - Leader restart triggering follower re-election
   - Local sessions not surviving the re-election process

The test should either:
1. Not use local sessions if it needs session durability during restarts
2. Expect and handle `SessionExpiredException` when using local sessions with restarts
3. Use global sessions for the restart injection test
