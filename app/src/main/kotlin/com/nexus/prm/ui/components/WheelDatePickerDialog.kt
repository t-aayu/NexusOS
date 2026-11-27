package com.nexus.prm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.prm.data.PostingValidation
import java.util.Calendar

private val MONTH_LABELS = listOf(
  "01 Jan", "02 Feb", "03 Mar", "04 Apr", "05 May", "06 Jun",
  "07 Jul", "08 Aug", "09 Sep", "10 Oct", "11 Nov", "12 Dec",
)

@Composable
fun DatePickerField(
  label: String,
  value: String,
  onClick: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
  isError: Boolean = false,
  supportingText: @Composable (() -> Unit)? = null,
) {
  val displayValue = remember(value) { PostingValidation.formatForDisplay(value) }
  Box(modifier = modifier.fillMaxWidth()) {
    OutlinedTextField(
      value = displayValue,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      placeholder = { Text("DD-MM-YYYY") },
      singleLine = true,
      isError = isError,
      supportingText = supportingText,
      leadingIcon = {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
      },
      trailingIcon = {
        if (displayValue.isNotEmpty()) {
          IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = "Clear date")
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
    // Transparent clickable overlay (excluding trailing clear button area)
    Box(
      modifier = Modifier
        .matchParentSize()
        .padding(end = if (displayValue.isNotEmpty()) 48.dp else 0.dp)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClick,
        ),
    )
  }
}

@Composable
fun WheelDatePickerDialog(
  title: String,
  initialDate: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  val today = remember {
    Calendar.getInstance().let { cal ->
      PostingValidation.SimpleDate(
        year = cal.get(Calendar.YEAR),
        month = cal.get(Calendar.MONTH) + 1,
        day = cal.get(Calendar.DAY_OF_MONTH),
      )
    }
  }
  val seed = remember(initialDate) {
    PostingValidation.parseDate(initialDate) ?: today
  }

  val years = remember { (1950..2050).toList() }
  var selectedYear by remember { mutableIntStateOf(seed.year.coerceIn(1950, 2050)) }
  var selectedMonth by remember { mutableIntStateOf(seed.month.coerceIn(1, 12)) }
  var selectedDay by remember { mutableIntStateOf(seed.day.coerceIn(1, 31)) }

  val maxDays = remember(selectedMonth, selectedYear) {
    PostingValidation.daysInMonth(selectedMonth, selectedYear)
  }
  if (selectedDay > maxDays) {
    selectedDay = maxDays
  }
  val days = remember(maxDays) { (1..maxDays).toList() }

  val formattedPreview = PostingValidation.SimpleDate(selectedYear, selectedMonth, selectedDay).formatDdMmYyyy()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
          text = formattedPreview,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          Text("DD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("MM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("YYYY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(144.dp),
          contentAlignment = Alignment.Center,
        ) {
          // Center selection highlight
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            WheelColumn(
              items = days,
              selectedIndex = (selectedDay - 1).coerceIn(0, days.lastIndex),
              onSelectedIndexChange = { idx -> selectedDay = days[idx] },
              formatItem = { "%02d".format(it) },
              modifier = Modifier.weight(1f),
            )
            WheelColumn(
              items = (1..12).toList(),
              selectedIndex = (selectedMonth - 1).coerceIn(0, 11),
              onSelectedIndexChange = { idx -> selectedMonth = idx + 1 },
              formatItem = { MONTH_LABELS[it - 1] },
              modifier = Modifier.weight(1.3f),
            )
            WheelColumn(
              items = years,
              selectedIndex = (selectedYear - 1950).coerceIn(0, years.lastIndex),
              onSelectedIndexChange = { idx -> selectedYear = years[idx] },
              formatItem = { it.toString() },
              modifier = Modifier.weight(1.1f),
            )
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(formattedPreview) }) {
        Text("Set date")
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onConfirm("") }) {
          Text("Clear")
        }
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    },
  )
}

@Composable
private fun <T> WheelColumn(
  items: List<T>,
  selectedIndex: Int,
  onSelectedIndexChange: (Int) -> Unit,
  formatItem: (T) -> String,
  modifier: Modifier = Modifier,
) {
  val itemHeight = 48.dp
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
  val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

  val centeredIndex by remember(items.size) {
    derivedStateOf {
      val firstIdx = listState.firstVisibleItemIndex
      val offset = listState.firstVisibleItemScrollOffset
      val candidate = if (offset > 60) firstIdx + 1 else firstIdx
      candidate.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
  }

  LaunchedEffect(listState.isScrollInProgress, centeredIndex) {
    if (!listState.isScrollInProgress && centeredIndex != selectedIndex && items.isNotEmpty()) {
      onSelectedIndexChange(centeredIndex)
    }
  }

  LaunchedEffect(selectedIndex, items.size) {
    if (!listState.isScrollInProgress && selectedIndex in items.indices && listState.firstVisibleItemIndex != selectedIndex) {
      listState.scrollToItem(selectedIndex)
    }
  }

  LazyColumn(
    state = listState,
    flingBehavior = flingBehavior,
    contentPadding = PaddingValues(vertical = itemHeight),
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.height(itemHeight * 3),
  ) {
    items(items.size) { index ->
      val isCentered = index == centeredIndex
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(itemHeight),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = formatItem(items[index]),
          style = if (isCentered) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
          fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
          color = if (isCentered) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.alpha(if (isCentered) 1f else 0.6f),
        )
      }
    }
  }
}
