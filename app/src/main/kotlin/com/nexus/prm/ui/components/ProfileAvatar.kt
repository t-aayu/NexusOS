package com.nexus.prm.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileAvatar(
  name: String,
  photoFileName: String?,
  loadPhotoBytes: suspend (String) -> ByteArray?,
  modifier: Modifier = Modifier,
  size: Dp = 48.dp,
  overridePhotoBytes: ByteArray? = null,
) {
  val bitmap by produceState<ImageBitmap?>(
    initialValue = null,
    key1 = photoFileName,
    key2 = overridePhotoBytes,
  ) {
    val bytes = overridePhotoBytes ?: photoFileName?.let { loadPhotoBytes(it) }
    value = bytes?.let { data ->
      BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
    }
  }

  val currentBitmap = bitmap
  if (currentBitmap != null) {
    Image(
      bitmap = currentBitmap,
      contentDescription = name,
      contentScale = ContentScale.Crop,
      modifier = modifier
        .size(size)
        .clip(CircleShape),
    )
  } else {
    val initials = computeInitials(name)
    val fontSize = (size.value * 0.36f).sp
    Box(
      modifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = initials,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize,
      )
    }
  }
}

private fun computeInitials(name: String): String {
  val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
  return when {
    parts.isEmpty() -> "?"
    parts.size == 1 -> parts[0].take(2).uppercase()
    else -> "${parts.first().first()}${parts.last().first()}".uppercase()
  }
}
