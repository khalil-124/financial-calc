package com.khalil.calc.logic

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// A professional Cloud Sync interface.
// When ready, you can implement FirebaseSyncServiceImpl : CloudSyncService
interface CloudSyncService {
    val syncStatus: StateFlow<SyncStatus>

    suspend fun syncLoansToCloud(loans: List<SavedLoan>)
    suspend fun enableCloudSync(enabled: Boolean)
}

enum class SyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR_AUTH,
    ERROR_NETWORK,
    DISABLED
}

class MockCloudSyncServiceImpl : CloudSyncService {
    private val _syncStatus = MutableStateFlow(SyncStatus.DISABLED)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    override suspend fun syncLoansToCloud(loans: List<SavedLoan>) {
        if (_syncStatus.value == SyncStatus.DISABLED) return

        _syncStatus.value = SyncStatus.SYNCING

        // Simulate a secure network operation (e.g. Firebase Firestore batch write)
        delay(1500)

        // Simulated success
        _syncStatus.value = SyncStatus.SUCCESS

        // Revert to idle after 3 seconds
        delay(3000)
        if (_syncStatus.value == SyncStatus.SUCCESS) {
             _syncStatus.value = SyncStatus.IDLE
        }
    }

    override suspend fun enableCloudSync(enabled: Boolean) {
        if (enabled) {
            _syncStatus.value = SyncStatus.IDLE
        } else {
            _syncStatus.value = SyncStatus.DISABLED
        }
    }
}
