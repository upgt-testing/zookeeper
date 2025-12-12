# Test Transformation Tracker

This document tracks the progress of adding restart positions to tests.

- `- [ ]` = Not started
- `- [x]` = Finished

## Progress Overview

Total Tests: 119
- [ ] Completed: 0/119

---

## ClientBase Tests (79 tests)

- [ ] ClientBase - ZooKeeperTest
- [ ] ClientBase - ZKUtilTest
- [ ] ClientBase - DistributedQueueTest
- [ ] ClientBase - NullDataTest
- [ ] ClientBase - SyncCallTest
- [ ] ClientBase - KeyAuthClientTest
- [ ] ClientBase - TimeTest
- [ ] ClientBase - RemoveWatchesCmdTest
- [ ] ClientBase - GetAllChildrenNumberTest
- [ ] ClientBase - EnsembleAuthTest
- [ ] ClientBase - DigestAuthDisabledTest
- [ ] ClientBase - DisconnectedWatcherTest
- [ ] ClientBase - ServerCnxnTest
- [ ] ClientBase - StandaloneServerAuditTest
- [ ] ClientBase - SaslTestUtil
- [ ] ClientBase - GetEphemeralsTest
- [ ] ClientBase - ClientTest
- [ ] ClientBase - WriteLockTest
- [ ] ClientBase - AuthTest
- [ ] ClientBase - CheckTest
- [ ] ClientBase - MultiAsyncTransactionTest
- [ ] ClientBase - ClientHammerTest
- [ ] ClientBase - MaxCnxnsTest
- [ ] ClientBase - ThrottledOpStandaloneTest
- [ ] ClientBase - ACLRootTest
- [ ] ClientBase - SSLAuthTest
- [ ] ClientBase - FourLetterWordsTest
- [ ] ClientBase - SessionTimeoutTest
- [ ] ClientBase - ZooKeeperQuotaTest
- [ ] ClientBase - PersistentRecursiveWatcherTest
- [ ] ClientBase - WatcherTest
- [ ] ClientBase - LeaderElectionSupportTest
- [ ] ClientBase - SessionInvalidationTest
- [ ] ClientBase - EnforceQuotaTest
- [ ] ClientBase - ChrootTest
- [ ] ClientBase - AsyncOpsTest
- [ ] ClientBase - ServerIdTest
- [ ] ClientBase - MultiOperationTest
- [ ] ClientBase - CreateTest
- [ ] ClientBase - GetChildren2Test
- [ ] ClientBase - LogChopperTest
- [ ] ClientBase - ClientRetryTest
- [ ] ClientBase - HierarchicalQuorumTest
- [ ] ClientBase - UnsupportedAddWatcherTest
- [ ] ClientBase - SaslKerberosAuthOverSSLTest
- [ ] ClientBase - ResponseCacheTest
- [ ] ClientBase - StatTest
- [ ] ClientBase - WatcherFuncTest
- [ ] ClientBase - LoadFromLogTest
- [ ] ClientBase - PersistentWatcherTest
- [ ] ClientBase - NIOServerCnxnTest
- [ ] ClientBase - PersistentWatcherACLTest
- [ ] ClientBase - RemoveWatchesTest
- [ ] ClientBase - NettyServerCnxnTest
- [ ] ClientBase - FourLetterWordsWhiteListTest
- [ ] ClientBase - PrepRequestProcessorTest
- [ ] ClientBase - CreateTTLTest
- [ ] ClientBase - TxnLogDigestTest
- [ ] ClientBase - SnapshotDigestTest
- [ ] ClientBase - Emulate353TTLTest
- [ ] ClientBase - InvalidSnapshotTest
- [ ] ClientBase - CommandsTest
- [ ] ClientBase - CreateContainerTest
- [ ] ClientBase - BufferSizeTest
- [ ] ClientBase - NettyServerCnxnFactoryTest
- [ ] ClientBase - SaslAuthRequiredFailNoSASLTest
- [ ] ClientBase - SaslAuthMissingClientConfigTest
- [ ] ClientBase - SaslDigestAuthOverSSLTest
- [ ] ClientBase - SaslAuthFailTest
- [ ] ClientBase - SaslAuthRequiredFailWrongSASLTest
- [ ] ClientBase - SaslAuthFailDesignatedClientTest
- [ ] ClientBase - SaslSuperUserTest
- [ ] ClientBase - SaslAuthRequiredTest
- [ ] ClientBase - SaslAuthDesignatedServerTest
- [ ] ClientBase - SaslAuthDesignatedClientTest
- [ ] ClientBase - SaslAuthRequiredMultiClientTest
- [ ] ClientBase - SaslAuthTest
- [ ] ClientBase - QuorumOracleMajTest

## QuorumBase Tests (7 tests)

- [ ] QuorumBase - QuorumMajorityTest
- [ ] QuorumBase - QuorumQuotaTest
- [ ] QuorumBase - ThrottledOpQuorumTest
- [ ] QuorumBase - FourLetterWordsQuorumTest
- [ ] QuorumBase - ThrottledOpObserverTest
- [ ] QuorumBase - EagerACLFilterTest
- [ ] QuorumBase - QuorumRequestPipelineTest
- [ ] QuorumBase - MultiOpSessionUpgradeTest

## QuorumPeerTestBase Tests (33 tests)

- [ ] QuorumPeerTestBase - ClientCnxnSocketFragilityTest
- [ ] QuorumPeerTestBase - ClientSSLTest
- [ ] QuorumPeerTestBase - ObserverTest
- [ ] QuorumPeerTestBase - Slf4JAuditLoggerTest
- [ ] QuorumPeerTestBase - NonRecoverableErrorTest
- [ ] QuorumPeerTestBase - StandaloneTest
- [ ] QuorumPeerTestBase - ClientRequestTimeoutTest
- [ ] QuorumPeerTestBase - EnforceAuthenticationTest
- [ ] QuorumPeerTestBase - RestoreQuorumTest
- [ ] QuorumPeerTestBase - StandaloneDisabledTest
- [ ] QuorumPeerTestBase - FuzzySnapshotRelatedTest
- [ ] QuorumPeerTestBase - CloseSessionTxnTest
- [ ] QuorumPeerTestBase - CurrentEpochWriteFailureTest
- [ ] QuorumPeerTestBase - ZooKeeperServerMaxCnxnsTest
- [ ] QuorumPeerTestBase - DIFFSyncConsistencyTest
- [ ] QuorumPeerTestBase - EphemeralNodeDeletionTest
- [ ] QuorumPeerTestBase - SessionUpgradeQuorumTest
- [ ] QuorumPeerTestBase - ReconfigDuringLeaderSyncTest
- [ ] QuorumPeerTestBase - RaceConditionTest
- [ ] QuorumPeerTestBase - ReconfigFailureCasesTest
- [ ] QuorumPeerTestBase - QuorumPeerMainTest
- [ ] QuorumPeerTestBase - QuorumDigestTest
- [ ] QuorumPeerTestBase - ReconfigRecoveryTest
- [ ] QuorumPeerTestBase - QuorumPeerMainMultiAddressTest
- [ ] QuorumPeerTestBase - ReconfigLegacyTest
- [ ] QuorumPeerTestBase - LearnerMetricsTest
- [ ] QuorumPeerTestBase - ReconfigBackupTest
- [ ] QuorumPeerTestBase - QuorumSSLTest
- [ ] QuorumPeerTestBase - ReconfigRollingRestartCompatibilityTest
- [ ] QuorumPeerTestBase - EpochWriteFailureTest
- [ ] QuorumPeerTestBase - DIFFSyncTest
- [ ] QuorumPeerTestBase - ObserverMasterTest
- [ ] QuorumPeerTestBase - FollowerRequestProcessorTest

---

## Notes

- Transformation involves adding restart positions to each test
- Mark with `[x]` when transformation is complete
- Update the "Completed" count in Progress Overview as tests are finished
