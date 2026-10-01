package dev.saketanand.setwise.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SetwiseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),  // tags
    small = RoundedCornerShape(8.dp),       // chips
    medium = RoundedCornerShape(12.dp),     // inputs, set rows
    large = RoundedCornerShape(16.dp),      // cards
    extraLarge = RoundedCornerShape(28.dp), // bottom sheets, dialogs
)
