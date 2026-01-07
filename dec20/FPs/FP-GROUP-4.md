# FP-GROUP-4: ClientBaseAdapter Does Not Support "leader"/"follower" Roles for Custom 2-Node Quorum

## Summary

This failure group is a **False Positive (FP)** because the failure is caused by an improper adapter selection in the restart testing framework, not a bug in ZooKeeper's source code.

## Failure Details

**Test Class:** `org.apache.zookeeper.test.QuorumOracleMajTest_RestartInjected`
**Test Method:** `testMajQuorums`
**Restart Positions:** `after_jmx_verification`, `after_setup`, `after_get_leader`
**Restart Targets:** `leader`, `follower`
**Restart Mode:** `GRACEFUL`
**Total Occurrences:** 6 test executions

## Error Message

```
java.lang.IllegalArgumentException: Unknown node role: leader. Supported: server, all
	at org.restarttest.adapter.zookeeper.ClientBaseAdapter.restartNode(ClientBaseAdapter.java:59)
```

## Root Cause Analysis

### 1. Test Class Hierarchy

The test class has the following inheritance:
```
QuorumOracleMajTest_RestartInjected
    └── QuorumBaseOracle_2Nodes
            └── ClientBase
```

### 2. Adapter Selection Issue

The restart framework selects adapters based on the class type hierarchy:
- Since `ClientBase` is the parent class, `ClientBaseAdapter` is selected
- `ClientBaseAdapter` is designed for **standalone single-node ZooKeeper tests**

### 3. ClientBaseAdapter Limitations (ClientBaseAdapter.java:53-61)

```java
@Override
public void restartNode(ClientBase cluster, String nodeRole, int nodeIndex, RestartMode mode) throws Exception {
    String normalizedRole = normalizeRole(nodeRole);

    if ("server".equals(normalizedRole) || "all".equals(normalizedRole)) {
        restartServer(cluster, mode);
    } else {
        throw new IllegalArgumentException(
            "Unknown node role: " + nodeRole + ". Supported: server, all");
    }
}
```

The `ClientBaseAdapter` only supports "server" and "all" roles because:
- A standalone ZooKeeper server has no leader/follower concept
- It's designed for single-node test scenarios

### 4. The Mismatch

`QuorumBaseOracle_2Nodes` is a **custom 2-node quorum cluster test** that:
- Creates 2 `QuorumPeer` instances (s1 and s2)
- Has leader election and leader/follower roles
- But extends `ClientBase` (instead of `QuorumBase`)

The existing `QuorumBaseAdapter` supports leader/follower roles (QuorumBaseAdapter.java:59-61):
```java
if ("leader".equals(normalizedRole) || "master".equals(normalizedRole)) {
    restartLeader(cluster, mode);
} else if ("follower".equals(normalizedRole) || "worker".equals(normalizedRole)) {
    restartFollower(cluster, nodeIndex, mode);
}
```

However, `QuorumBaseAdapter`:
- Expects clusters of type `QuorumBase` (not `ClientBase`)
- Is hardcoded for 5-node clusters (s1-s5)
- Cannot work with the 2-node `QuorumBaseOracle_2Nodes`

## Why This Is a False Positive

1. **Not a ZooKeeper Bug:** The failure is entirely within the restart testing infrastructure
2. **Adapter/Framework Limitation:** The framework cannot properly handle custom test base classes like `QuorumBaseOracle_2Nodes` that don't fit the standard `ClientBase` (standalone) or `QuorumBase` (5-node) patterns
3. **Improper Restart Position:** Attempting to restart "leader" or "follower" nodes on a test that uses `ClientBase` as its adapter type is invalid

## Potential Framework Improvements

To properly test this class, one would need to:
1. Create a custom adapter for `QuorumBaseOracle_2Nodes` that:
   - Handles 2-node quorums (s1 and s2)
   - Supports leader/follower role detection and restart
2. Or modify the restart injection to use "server" target with specific indices instead of semantic roles

## Affected Test Executions

| Position | Target | Execution Dir |
|----------|--------|---------------|
| `after_jmx_verification` | `leader` | `002-e7253efd` |
| `after_setup` | `follower` | `003-17287372` |
| `after_get_leader` | `leader` | `004-2f5441b1` |
| (and 3 more) | | |

## Conclusion

This is a **False Positive** caused by the restart testing framework's adapter selection mechanism not having proper support for the custom 2-node quorum test base class `QuorumBaseOracle_2Nodes`. The test framework selects `ClientBaseAdapter` based on the class hierarchy, but this adapter doesn't support the "leader"/"follower" roles that the test tries to use.
