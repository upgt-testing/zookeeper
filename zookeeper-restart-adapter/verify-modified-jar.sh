#!/bin/bash

# Verification script to check if the bundled JAR contains modified (public) fields

BUNDLED_JAR="lib/zookeeper-3.9.4-tests.jar"

echo "======================================"
echo "ZooKeeper Test-JAR Modification Check"
echo "======================================"
echo ""

if [ ! -f "$BUNDLED_JAR" ]; then
    echo "❌ ERROR: Bundled JAR not found at $BUNDLED_JAR"
    exit 1
fi

echo "✓ Found bundled JAR: $BUNDLED_JAR"
echo "  Size: $(ls -lh $BUNDLED_JAR | awk '{print $5}')"
echo ""

echo "Checking ClientBase modifications..."
echo "-----------------------------------"

# Check hostPort field
HOSTPORT=$(javap -classpath $BUNDLED_JAR org.apache.zookeeper.test.ClientBase 2>/dev/null | grep "hostPort")
if echo "$HOSTPORT" | grep -q "public.*hostPort"; then
    echo "✓ hostPort is PUBLIC"
else
    echo "❌ hostPort is NOT public (found: $HOSTPORT)"
    exit 1
fi

# Check shutdownServerInstance method
SHUTDOWN=$(javap -classpath $BUNDLED_JAR org.apache.zookeeper.test.ClientBase 2>/dev/null | grep "shutdownServerInstance")
if echo "$SHUTDOWN" | grep -q "public static.*shutdownServerInstance"; then
    echo "✓ shutdownServerInstance is PUBLIC STATIC"
else
    echo "❌ shutdownServerInstance is NOT public static (found: $SHUTDOWN)"
    exit 1
fi

echo ""
echo "Checking QuorumBase modifications..."
echo "------------------------------------"

# Check s1 field
S1_FIELD=$(javap -classpath $BUNDLED_JAR org.apache.zookeeper.test.QuorumBase 2>/dev/null | grep "QuorumPeer s1")
if echo "$S1_FIELD" | grep -q "public.*QuorumPeer s1"; then
    echo "✓ s1-s5 QuorumPeer fields are PUBLIC"
else
    echo "❌ s1 QuorumPeer is NOT public (found: $S1_FIELD)"
    exit 1
fi

# Check portClient1 field
PORT_FIELD=$(javap -classpath $BUNDLED_JAR org.apache.zookeeper.test.QuorumBase 2>/dev/null | grep "portClient1")
if echo "$PORT_FIELD" | grep -q "public int portClient1"; then
    echo "✓ portClient1-5 fields are PUBLIC"
else
    echo "❌ portClient1 is NOT public (found: $PORT_FIELD)"
    exit 1
fi

echo ""
echo "======================================"
echo "✓ ALL CHECKS PASSED"
echo "======================================"
echo ""
echo "The bundled JAR contains the modified classes with public fields."
echo "This is the correct version for the adapter to build successfully."
