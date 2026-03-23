package de.laurik.openalarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.laurik.openalarm.ui.theme.bounce
import de.laurik.openalarm.ui.theme.bounceClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight

@Composable
fun EditGroupDialog(
    group: AlarmGroup,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    onDelete: ((Boolean) -> Unit)? = null
) {
    var name by remember { mutableStateOf(group.name) }
    var selectedColor by remember { mutableIntStateOf(group.colorArgb) }
    val isNewGroup = group.id.isEmpty() // Assuming empty ID means new group
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val groupColors = listOf(0xFFE3F2FD, 0xFFF3E5F5, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFFFEBEE, 0xFFE0F7FA)

    // Delete confirmation
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_group_name, group.name)) },
            text = { Text(stringResource(R.string.groups_delete_alarms_too)) },
            confirmButton = {
                TextButton(onClick = { onDelete(false); showDeleteConfirm = false; onDismiss() }) {
                    Text(stringResource(R.string.group_delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDelete(true); showDeleteConfirm = false; onDismiss() }) {
                    Text(stringResource(R.string.group_delete_keep_alarms))
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp)) {
                Text(if (isNewGroup) stringResource(R.string.label_new_group) else stringResource(R.string.edit_group), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hint_group_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.label_group_color), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupColors.forEach { colorLong ->
                        val colorInt = colorLong.toInt()
                        val isSelected = selectedColor == colorInt
                        Box(
                            modifier = Modifier
                                .size(48.dp) // Larger touch target
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                                .bounceClickable(indication = LocalIndication.current) { selectedColor = colorInt },
                            contentAlignment = Alignment.Center
                        ) {
                             if (isSelected) {
                                 Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black.copy(alpha=0.7f))
                             }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                // Delete button
                if (onDelete != null) {
                    val deleteIS = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().bounce(deleteIS),
                        interactionSource = deleteIS,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.desc_delete))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val cancelIS = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).bounce(cancelIS),
                        interactionSource = cancelIS
                    ) { Text(stringResource(R.string.action_cancel)) }

                    val saveIS = remember { MutableInteractionSource() }
                    Button(
                        onClick = { onSave(name, selectedColor) },
                        modifier = Modifier.weight(1.5f).bounce(saveIS).height(56.dp),
                        interactionSource = saveIS,
                        shape = MaterialTheme.shapes.medium
                    ) { 
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) 
                    }
                }
            }
        }
    }
}