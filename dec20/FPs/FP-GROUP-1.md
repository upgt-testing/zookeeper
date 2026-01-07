# FP-GROUP-1: MainThreadAdapter Unable to Get Client Port

## Summary
This failure group is a **False Positive (FP)** caused by the restart adapter's inability to obtain the client port from `QuorumPeerTestBase.MainThread` due to an incomplete implementation in certain constructor chains of the test utility class.

## Root Cause Analysis

### The Problem
The failure manifests as:
```
java.lang.Exception: QuorumPeer failed to start at 127.0.0.1:0
    at org.restarttest.adapter.zookeeper.MainThreadAdapter.waitActive(MainThreadAdapter.java:83)
```

### Technical Details

1. **MainThread Constructor Chain Bug**: The `QuorumPeerTestBase.MainThread` class in ZooKeeper's test infrastructure has multiple constructor chains. Some chains (specifically the one at lines 175 -> 195 -> 203) do NOT initialize the `this.clientPort` member variable, even though they accept a `clientPort` parameter and write it to the config file.

   The constructor at line 116-161 properly sets `this.clientPort = clientPort;` (line 119), but the chain starting at line 175 does not.

2. **Impact on Restart Adapter**: When the test uses a constructor that doesn't set `clientPort`, calling `MainThread.getClientPort()` returns 0 (default int value). The restart adapter relied on this method to know which port to wait for after restart.

3. **Timing Issue**: After restart, the QuorumPeer hasn't been created yet when `waitActive()` is called, so getting the port from `QuorumPeer.getClientPort()` is also not possible at that point.

### Fix Applied
The restart adapter was fixed to:
1. Capture the client port from `QuorumPeer.getClientPort()` BEFORE shutdown (when QuorumPeer is still running)
2. Store this saved port in an instance variable
3. Use the saved port in `waitActive()` after restart
4. Fallback to reading the port from the config file if needed

## Why This is a False Positive

1. **No ZooKeeper Production Code Bug**: The failure is NOT caused by any bug in ZooKeeper's production code. The NullPointerException/failure is entirely in the restart testing infrastructure.

2. **Test Infrastructure Deficiency**: The issue is with ZooKeeper's `MainThread` test utility class having inconsistent constructor implementations where some paths don't initialize all member variables.

3. **Adapter Integration Issue**: The restart adapter was designed to use `MainThread.getClientPort()` which doesn't work for all test configurations. This is an integration issue, not a ZooKeeper bug.

4. **Test Passes After Adapter Fix**: With the fix applied to the restart adapter, the test passes successfully and no bug is revealed in ZooKeeper's behavior.

## Affected Tests
This FP affects 190 test executions that use the `MainThread` class with constructor chains that don't initialize `clientPort`:
- `EphemeralNodeDeletionTest_RestartInjected#testEphemeralNodeDeletion`
- And other tests using similar MainThread configurations

## Resolution
The restart adapter was enhanced to be more robust when dealing with incomplete test infrastructure implementations. The fix involves:
- Getting the client port from QuorumPeer before shutdown
- Storing the port for use after restart
- Multiple fallback strategies (saved port, QuorumPeer, config file)

## Files Modified
- `zookeeper-restart-adapter/src/main/java/org/restarttest/adapter/zookeeper/MainThreadAdapter.java`
  - Added `savedClientPort` field to store port before shutdown
  - Enhanced `restartMainThread()` to capture port from QuorumPeer
  - Enhanced `waitActive()` to use saved port and fallback to config file
  - Added `readClientPortFromConfig()` method for parsing client port from config files
