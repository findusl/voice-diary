package de.lehrbaum.voiry.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmationDialog(
	title: String,
	text: String,
	confirmText: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
	dismissText: String = "Cancel",
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Text(text) },
		confirmButton = {
			TextButton(onClick = onConfirm) { Text(confirmText) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(dismissText) }
		},
	)
}
