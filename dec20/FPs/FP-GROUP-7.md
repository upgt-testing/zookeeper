# FP-GROUP-7: No Followers Found in Cluster During Leader Election

## Classification: FALSE POSITIVE

## Summary
The failure occurs when the restart framework attempts to restart a "follower" immediately after the leader has been shut down. At this point, the cluster is in leader election mode and no nodes are in the FOLLOWING state.

## Root Cause Analysis

### The Failure Scenario
The test `CheckTest_RestartInjected.testClusterDatabaseReloadAfterCheck` follows this sequence:

1. Set up a quorum cluster with observers (`qb.setUp(true, true)`)
2. Get the leader reference
3. Run test operations
4. Execute restart at `after_leader_test_operations` (restarts the leader)
5. **`qb.shutdown(leader)` - shuts down the leader**
6. Execute restart at `after_leader_shutdown` - tries to restart a "follower"

### Why "No Followers Found"

When the leader is shut down (step 5), the ZooKeeper cluster enters **leader election mode**:

1. All remaining voting nodes transition from `FOLLOWING` to `LOOKING` state
2. The leader election protocol (Fast Leader Election) begins
3. Until a new leader is elected and the cluster stabilizes, no nodes are in `FOLLOWING` state

The `QuorumBaseAdapter.restartFollower()` method correctly checks for nodes in `FOLLOWING` state:

```java
// QuorumBaseAdapter.java:191-203
private void restartFollower(QuorumBase cluster, int followerIndex, RestartMode mode) throws Exception {
    List<Integer> followerIndices = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
        QuorumPeer peer = getServerByIndex(cluster, i);
        if (peer != null && peer.getPeerState() == ServerState.FOLLOWING) {
            followerIndices.add(i);
        }
    }

    if (followerIndices.isEmpty()) {
        throw new Exception("No followers found in cluster");  // <-- This is thrown
    }
    ...
}
```

### Cluster State at Failure Point
- **Leader:** Shut down (no longer available)
- **Previous Followers:** Now in `LOOKING` state (participating in election)
- **Observers:** In `OBSERVING` state (do not participate in election)

## Why This is a False Positive

1. **Not a ZooKeeper Source Code Bug:** ZooKeeper's behavior is correct - when the leader is lost, nodes enter election mode.

2. **Not a Test Code Bug:** The test logic (`testClusterDatabaseReloadAfterCheck`) is testing a valid scenario - database reload after leader shutdown.

3. **Restart Framework Working Correctly:** The adapter correctly identifies that no followers exist at that moment.

4. **Improper Restart Position:** The restart injection point `after_leader_shutdown` is placed during a transitional cluster state where:
   - The cluster is in leader election
   - No stable follower role exists
   - Attempting to restart by role ("follower") is not meaningful

## Affected Test Executions

Both test executions in Group 7 suffer from the same root cause:

### Example 1
- **Test:** `org.apache.zookeeper.test.CheckTest_RestartInjected#testClusterDatabaseReloadAfterCheck`
- **Position:** `after_leader_shutdown`
- **Target:** `follower`
- **Issue:** Leader just shut down, no followers in FOLLOWING state

### Example 2
- **Test:** `org.apache.zookeeper.test.CheckTest_RestartInjected#testClusterDatabaseReloadAfterCheck`
- **Position:** `after_database_load`
- **Target:** `follower`
- **Issue:** Same as above - leader is still shut down, cluster may still be in election or transitioning

## Recommendation

To properly test restart scenarios after leader shutdown:

1. **Wait for new leader election:** Add a wait period or check for new leader before attempting follower restart
2. **Use index-based restart instead of role-based:** Use `restart("server")` with specific index instead of `restart("follower")`
3. **Adjust restart position:** Move the restart injection point to after the cluster has stabilized with a new leader

## Conclusion

This is a false positive caused by attempting to restart a role-based node ("follower") during a cluster state transition (leader election) when no nodes occupy that role. The failure correctly identifies an invalid restart operation rather than exposing a bug in ZooKeeper.
