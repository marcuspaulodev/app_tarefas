package com.marcuspaulo.tarefas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.ui.theme.parseHexColor

@Composable
fun TagChip(
    tag: Tag,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val color = parseHexColor(tag.colorHex)
    val background = if (selected) color.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent

    Text(
        text = tag.name,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, color, RoundedCornerShape(50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
