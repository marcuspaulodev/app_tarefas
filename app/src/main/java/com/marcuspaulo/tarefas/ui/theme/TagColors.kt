package com.marcuspaulo.tarefas.ui.theme

import androidx.compose.ui.graphics.Color

val TAG_COLOR_PALETTE = listOf(
    "#3D5AFE", // azul
    "#00C853", // verde
    "#FF6D00", // laranja
    "#D500F9", // roxo
    "#F50057", // rosa
    "#00B8D4", // ciano
    "#FFAB00", // amarelo
    "#6D4C41"  // marrom
)

fun parseHexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
