# ZOOKEEPER-XXXX: QuorumPeerTestBase.MainThread constructor chain does not initialize member variables

## Summary
The `QuorumPeerTestBase.MainThread` test utility class has inconsistent constructor implementations. One constructor chain (lines 203-260) does not initialize the `clientPort`, `myid`, `quorumCfgSection`, and `otherConfigs` member variables, causing `getClientPort()` and `getMyid()` to return incorrect default values (0).

## Component
- **Component:** tests
- **Affects Version:** 3.9.x (and likely earlier versions)
- **Priority:** Minor
- **Type:** Bug

## Description

### Problem
The `MainThread` class in `QuorumPeerTestBase.java` has two main constructor chains:

#### 1. Constructor at lines 116-161 - CORRECT
This constructor properly initializes all member variables:

```java
public MainThread(int myid, int clientPort, String quorumCfgSection,
                  Map<String, String> otherConfigs, int tickTime) throws IOException {
    baseDir = ClientBase.createTmpDir();
    this.myid = myid;
    this.clientPort = clientPort;           // ✓ CORRECTLY INITIALIZED
    this.quorumCfgSection = quorumCfgSection; // ✓ CORRECTLY INITIALIZED
    this.otherConfigs = otherConfigs;        // ✓ CORRECTLY INITIALIZED
    LOG.info("id = {} tmpDir = {} clientPort = {}", myid, baseDir, clientPort);
    confFile = new File(baseDir, "zoo.cfg");

    FileWriter fwriter = new FileWriter(confFile);
    fwriter.write("tickTime=" + tickTime + "\n");
    // ... writes config file ...
    fwriter.write("clientPort=" + clientPort + "\n");
    // ... rest of constructor ...
}
```

#### 2. Constructor at lines 203-260 - BUGGY
This constructor does NOT initialize member variables:

```java
public MainThread(int myid, int clientPort, int adminServerPort,
                  Integer secureClientPort, String quorumCfgSection,
                  String configs, String peerType,
                  boolean writeDynamicConfigFile, String version) throws IOException {
    tmpDir = ClientBase.createTmpDir();
    // ✗ MISSING: this.myid = myid;
    // ✗ MISSING: this.clientPort = clientPort;
    // ✗ MISSING: this.quorumCfgSection = quorumCfgSection;
    // ✗ MISSING: this.otherConfigs = ...;

    LOG.info("id = {} tmpDir = {} clientPort = {} adminServerPort = {}",
             myid, tmpDir, clientPort, adminServerPort);  // Logs it but doesn't store!

    File dataDir = new File(tmpDir, "data");
    // ... writes config file ...

    if (clientPort != UNSET_STATIC_CLIENTPORT) {
        fwriter.write("clientPort=" + clientPort + "\n");  // Writes to file but not to member!
    }
    // ... rest of constructor - member variables never assigned ...
}
```

The buggy constructor:
- Logs the clientPort value (line 205)
- Writes the clientPort to the config file (line 233)
- But **never assigns** `this.clientPort = clientPort;`

### Impact
When tests use a constructor that eventually calls the buggy constructor chain (lines 175 → 195 → 199 → 203), the following methods return incorrect values:
- `getClientPort()` returns 0 instead of the actual port
- `getMyid()` returns 0 instead of the actual myid
- `getQuorumCfgSection()` returns null
- `getOtherConfigs()` returns null

### Affected Constructor Chain
```
MainThread(int myid, int clientPort, String quorumCfgSection, boolean writeDynamicConfigFile)  // line 175
  → MainThread(int myid, int clientPort, int adminServerPort, String quorumCfgSection, String configs, String peerType, boolean writeDynamicConfigFile)  // line 195
    → MainThread(int myid, int clientPort, int adminServerPort, String quorumCfgSection, String configs, String peerType, boolean writeDynamicConfigFile, String version)  // line 199
      → MainThread(int myid, int clientPort, int adminServerPort, Integer secureClientPort, String quorumCfgSection, String configs, String peerType, boolean writeDynamicConfigFile, String version)  // line 203 - BUGGY
```

### Root Cause
The constructor at line 203-260 was added later (possibly for dynamic configuration support) but the developer forgot to add the member variable assignments that exist in the original constructor at line 116-161.

## Steps to Reproduce
```java
// Create MainThread using the affected constructor chain
MainThread mt = new MainThread(0, 12345, "server.0=...", false);
mt.start();
// Wait for server to start...

// These will return incorrect values:
System.out.println(mt.getClientPort());  // Returns 0, expected 12345
System.out.println(mt.getMyid());        // Returns 0, expected 0 (coincidentally correct)
```

## Proposed Fix
Add the missing member variable assignments to the constructor at line 203:

```java
public MainThread(int myid, int clientPort, int adminServerPort,
                  Integer secureClientPort, String quorumCfgSection,
                  String configs, String peerType,
                  boolean writeDynamicConfigFile, String version) throws IOException {
    tmpDir = ClientBase.createTmpDir();

    // ADD THESE LINES:
    this.myid = myid;
    this.clientPort = clientPort;
    this.quorumCfgSection = quorumCfgSection;
    this.otherConfigs = null;  // This constructor doesn't have otherConfigs parameter

    LOG.info("id = {} tmpDir = {} clientPort = {} adminServerPort = {}",
             myid, tmpDir, clientPort, adminServerPort);
    // ... rest of constructor unchanged ...
}
```

## Attachments
- See `dec20/patches/TEST-BUG-MainThread-clientPort.patch` for the complete fix

## Additional Context
This bug was discovered during restart testing with the RestartTestingFramework. The restart adapter called `MainThread.getClientPort()` to determine which port to wait for after restart, but received 0 instead of the actual port, causing tests to fail with:
```
java.lang.Exception: QuorumPeer failed to start at 127.0.0.1:0
```
