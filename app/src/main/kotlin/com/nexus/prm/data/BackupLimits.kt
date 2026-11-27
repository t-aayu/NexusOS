package com.nexus.prm.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Hard bounds for reading and expanding encrypted Nexus backup archives (`.nxbk`).
 * Limits are counted from actual bytes read rather than trusting file-size or ZIP metadata.
 *
 * Chosen to comfortably cover photo-heavy directories (up to ~1024px JPEG photos)
 * while rejecting corrupted streams, zip bombs, and memory-exhausting inputs.
 */
object BackupLimits {
  const val MAX_ENCRYPTED_BACKUP_BYTES = 128 * 1024 * 1024 // 128 MiB
  const val MAX_TOTAL_EXPANDED_BYTES = 192 * 1024 * 1024 // 192 MiB
  const val MAX_MANIFEST_ENTRY_BYTES = 32 * 1024 * 1024 // 32 MiB
  const val MAX_PHOTO_ENTRY_BYTES = 16 * 1024 * 1024 // 16 MiB
  const val MAX_ENTRY_COUNT = 5_000

  private const val READ_BUFFER_SIZE = 8 * 1024

  fun readEncryptedBackupBounded(
    input: InputStream,
    maxBytes: Int = MAX_ENCRYPTED_BACKUP_BYTES,
  ): ByteArray = readBounded(
    input = input,
    maxBytes = maxBytes,
    label = "Backup file",
  )

  fun readBounded(
    input: InputStream,
    maxBytes: Int,
    label: String,
  ): ByteArray {
    val buffer = ByteArray(READ_BUFFER_SIZE)
    val out = ByteArrayOutputStream()
    var total = 0
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      if (read == 0) continue
      if (total + read > maxBytes) {
        throw IllegalArgumentException(
          "$label exceeds the maximum allowed size (${formatLimitMb(maxBytes)}).",
        )
      }
      out.write(buffer, 0, read)
      total += read
    }
    return out.toByteArray()
  }

  private fun formatLimitMb(bytes: Int): String {
    val mb = bytes / (1024 * 1024)
    return if (mb >= 1) "$mb MB" else "$bytes bytes"
  }
}
