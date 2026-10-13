package com.bragro.mobile.ui.domain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/** Título de bloco com setinha de recolher/expandir à direita. */
@Composable
fun TituloRecolhivel(titulo: String, aberto: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher" else "Expandir")
    }
}
