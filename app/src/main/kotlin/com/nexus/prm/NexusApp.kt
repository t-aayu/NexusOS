package com.nexus.prm

import android.app.Application
import com.nexus.prm.data.AutoBackupManager
import com.nexus.prm.data.DirectoryRepository
import com.nexus.prm.data.KeystoreStorageCrypto
import com.nexus.prm.data.LocalDirectoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NexusApp : Application() {
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val storageCrypto by lazy { KeystoreStorageCrypto() }

  val directoryRepository: DirectoryRepository by lazy {
    LocalDirectoryRepository(
      filesDir = filesDir,
      crypto = storageCrypto,
    )
  }

  val autoBackupManager: AutoBackupManager by lazy {
    AutoBackupManager(
      context = applicationContext,
      filesDir = filesDir,
      crypto = storageCrypto,
      repository = directoryRepository,
    )
  }
}
