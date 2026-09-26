package com.mfp.filemanager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.rounded.AddToDrive
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mfp.filemanager.data.CategoryItem
import com.mfp.filemanager.data.FileModel

@Composable
fun GridItem(
    category: CategoryItem, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animatedProgress by animateFloatAsState(
        targetValue = category.progress, animationSpec = tween(
            durationMillis = 700, easing = LinearEasing
        ), label = "storage_progress"
    )


    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                // Open selected category
            }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category.id),
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }


            Spacer(
                modifier = Modifier.height(20.dp)
            )

            // Storage percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage used",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = category.storageUsedReadable,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            // Storage progress indicator
            LinearProgressIndicator(
                progress = {
                    animatedProgress
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(
                        RoundedCornerShape(50)
                    ),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private fun getCategoryIcon(
    categoryId: String
): ImageVector {
    return when (categoryId) {
        "1" -> Icons.Default.VideoLibrary
        "2" -> Icons.Default.Image
        "3" -> Icons.Default.LibraryBooks
        "4" -> Icons.Default.DocumentScanner
        else -> Icons.Default.InsertDriveFile
    }
}

@Composable
fun FileItem(
    fileName: String,
    subtitle: String,
    icon: ImageVector,
    fileModel: FileModel,
    modifier: Modifier = Modifier,
    onClick: (FileModel) -> Unit = {},
    onBackupFile: ((FileModel) -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = { onClick.invoke(fileModel) }),
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        headlineContent = {
            Text(
                text = fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Row() {
                if (onBackupFile != null) {
                    IconButton(onClick = { onBackupFile.invoke(fileModel) }) {
                        Icon(
                            Icons.Rounded.AddToDrive, contentDescription = "Backup to Drive"
                        )
                    }
                }

                if(onMoreClick != null) {
                    IconButton(onClick = { onMoreClick?.invoke() }) {
                        Icon(
                            Icons.Rounded.MoreVert, contentDescription = "More"
                        )
                    }
                }
            }

        },
        tonalElevation = 0.dp,

        )
}