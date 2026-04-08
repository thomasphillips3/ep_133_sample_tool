package com.ep133.sampletool.ui.sounds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ep133.sampletool.domain.model.EP133Pads
import com.ep133.sampletool.domain.model.EP133Sound
import com.ep133.sampletool.domain.model.Pad
import com.ep133.sampletool.domain.model.PadChannel
import com.ep133.sampletool.ui.theme.TEColors

/**
 * Bottom sheet showing the EP-133 3×4 pad grid for assigning a sound to a pad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadPickerSheet(
    sound: EP133Sound,
    group: PadChannel,
    onPadSelected: (Pad) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ASSIGN TO PAD",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = sound.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TEColors.Orange,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Group indicator
            Text(
                text = "GROUP ${group.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3×4 pad grid
            PadGrid(
                group = group,
                onPadTap = onPadSelected,
            )
        }
    }
}

@Composable
private fun PadGrid(
    group: PadChannel,
    onPadTap: (Pad) -> Unit,
) {
    val pads = EP133Pads.padsForChannel(group)
    val haptic = LocalHapticFeedback.current

    // 3 columns × 4 rows
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (row in 0 until 4) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    val pad = pads.getOrNull(index)
                    if (pad != null) {
                        PadCell(
                            pad = pad,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPadTap(pad)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PadCell(
    pad: Pad,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Extract the suffix (e.g. "7" from "A7", "." from "A.", "ENT" from "AENT")
    val suffix = pad.label.drop(1)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222323))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = suffix,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE2E3E4),
            textAlign = TextAlign.Center,
        )
    }
}
