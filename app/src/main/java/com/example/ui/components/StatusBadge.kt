package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.learning.mastery.MasteryStatus
import com.example.ui.theme.MasteryLearning
import com.example.ui.theme.MasteryLearningBg
import com.example.ui.theme.MasteryNeedsAttention
import com.example.ui.theme.MasteryNeedsAttentionBg
import com.example.ui.theme.MasteryNotAssessed
import com.example.ui.theme.MasteryNotAssessedBg
import com.example.ui.theme.MasteryStrong
import com.example.ui.theme.MasteryStrongBg

@Composable
fun NetworkStatusIndicator(
    isOnline: Boolean,
    isSimulatedOffline: Boolean,
    onToggleSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isOnline) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isOnline) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onToggleSimulation)
            .testTag("network_status_indicator"),
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = if (isOnline) "Internet connected" else "No internet connection",
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isOnline) "INTERNET" else "NO INTERNET",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            if (isSimulatedOffline) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(Preview)",
                    fontSize = 10.sp,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun MasteryBadge(
    status: MasteryStatus,
    score: Int? = null,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (status) {
        MasteryStatus.STRONG -> Triple(MasteryStrongBg, MasteryStrong, Icons.Default.CheckCircle)
        MasteryStatus.LEARNING -> Triple(MasteryLearningBg, MasteryLearning, Icons.Default.PlayCircleOutline)
        MasteryStatus.NEEDS_ATTENTION -> Triple(MasteryNeedsAttentionBg, MasteryNeedsAttention, Icons.Default.Warning)
        MasteryStatus.NOT_ASSESSED -> Triple(MasteryNotAssessedBg, MasteryNotAssessed, Icons.Default.HelpOutline)
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = status.label,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (score != null && status != MasteryStatus.NOT_ASSESSED) "${status.label} ($score%)" else status.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

@Composable
fun SourceCitationChip(
    docName: String,
    page: Int?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = "Source",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (page != null) "$docName — Page $page" else docName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MasteryProgressBar(
    progressPercent: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        progressPercent >= 75 -> MasteryStrong
        progressPercent >= 50 -> MasteryLearning
        progressPercent >= 25 -> MasteryNeedsAttention
        else -> MaterialTheme.colorScheme.primary
    }

    LinearProgressIndicator(
        progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
