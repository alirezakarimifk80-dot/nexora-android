package com.nexora.commandcenter.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 1. NEXORA Color, Typography, Spacing & Shape System
//    Aesthetic DNA: Nothing OS · Linear · Raycast
// ---------------------------------------------------------------------------
object NexoraColors {
    val ObsidianBlack = Color(0xFF040508)
    val GraphiteDark = Color(0xFF0A0C12)
    val GraphiteElevated = Color(0xFF111420)
    val SpecularBorder = Color(0xFF1F2437)
    val ElectricBlue = Color(0xFF2563EB)
    val CyberCyan = Color(0xFF00E5FF)
    val ElectricViolet = Color(0xFF7C3AED)
    val LuminousPurple = Color(0xFFA855F7)
    val EmeraldSignal = Color(0xFF10B981)
    val AmberSignal = Color(0xFFF59E0B)
    val CrimsonSignal = Color(0xFFEF4444)

    // Semantic aliases
    val DeepNavy = ObsidianBlack
    val MidnightSurface = GraphiteDark
    val NeonCyan = CyberCyan
    val NeonPurple = ElectricViolet
    val EmeraldOnline = EmeraldSignal
    val AmberWarning = AmberSignal
    val CoralDanger = CrimsonSignal

    val DaylightBg = Color(0xFFF4F6FB)
    val DaylightSurface = Color(0xFFFFFFFF)
    val DaylightBorder = Color(0xFFD8E0EE)
}

object NexoraSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object NexoraShapes {
    val Pill = RoundedCornerShape(999.dp)
    val Control = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(20.dp)
    val Modal = RoundedCornerShape(26.dp)
}

private val DarkColorScheme = darkColorScheme(
    primary = NexoraColors.CyberCyan,
    onPrimary = NexoraColors.ObsidianBlack,
    secondary = NexoraColors.ElectricViolet,
    onSecondary = Color.White,
    tertiary = NexoraColors.LuminousPurple,
    background = NexoraColors.ObsidianBlack,
    onBackground = Color(0xFFF8FAFC),
    surface = NexoraColors.GraphiteDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = NexoraColors.GraphiteElevated,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = NexoraColors.SpecularBorder,
    error = NexoraColors.CrimsonSignal
)

private val LightColorScheme = lightColorScheme(
    primary = NexoraColors.ElectricBlue,
    onPrimary = Color.White,
    secondary = NexoraColors.ElectricViolet,
    onSecondary = Color.White,
    tertiary = NexoraColors.LuminousPurple,
    background = NexoraColors.DaylightBg,
    onBackground = Color(0xFF090D16),
    surface = NexoraColors.DaylightSurface,
    onSurface = Color(0xFF090D16),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = NexoraColors.DaylightBorder,
    error = NexoraColors.CrimsonSignal
)

val NexoraTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun NexoraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = NexoraTypography,
        content = content
    )
}

// ---------------------------------------------------------------------------
// 2. Custom Geometric Icon System (Zero Emojis)
// ---------------------------------------------------------------------------
enum class NexoraGlyph {
    ORB,
    HOME,
    ASSISTANT,
    TOOLS,
    ANALYTICS,
    NETWORK,
    SETTINGS,
    CALCULATOR,
    QR,
    NOTES,
    FILES,
    TEXT,
    CONVERTER,
    TASKS,
    SHIELD,
    CHECK,
    WARNING,
    ERROR,
    REFRESH
}

@Composable
fun NexoraIcon(
    glyph: NexoraGlyph,
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height

        when (glyph) {
            NexoraGlyph.ORB, NexoraGlyph.ASSISTANT -> {
                drawCircle(color = tint, radius = w * 0.40f, style = stroke)
                drawCircle(color = tint.copy(alpha = 0.35f), radius = w * 0.24f)
                drawCircle(color = tint, radius = w * 0.10f)
            }
            NexoraGlyph.HOME -> {
                val path = Path().apply {
                    moveTo(w * 0.14f, h * 0.46f)
                    lineTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.86f, h * 0.46f)
                    lineTo(w * 0.86f, h * 0.86f)
                    lineTo(w * 0.14f, h * 0.86f)
                    close()
                }
                drawPath(path = path, color = tint, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.38f, h * 0.86f),
                    end = Offset(w * 0.38f, h * 0.60f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.62f, h * 0.86f),
                    end = Offset(w * 0.62f, h * 0.60f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            NexoraGlyph.TOOLS -> {
                val gap = w * 0.08f
                val boxSize = Size(w * 0.34f, h * 0.34f)
                val cr = CornerRadius(4.dp.toPx())
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.14f), boxSize, cr, stroke)
                drawRoundRect(tint, Offset(w * 0.48f + gap, h * 0.14f), boxSize, cr, stroke)
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.48f + gap), boxSize, cr, stroke)
                drawRoundRect(tint, Offset(w * 0.48f + gap, h * 0.48f + gap), boxSize, cr, stroke)
            }
            NexoraGlyph.ANALYTICS -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.84f), Offset(w * 0.20f, h * 0.48f), strokeWidth * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.84f), Offset(w * 0.50f, h * 0.18f), strokeWidth * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.80f, h * 0.84f), Offset(w * 0.80f, h * 0.36f), strokeWidth * 1.2f, StrokeCap.Round)
            }
            NexoraGlyph.NETWORK -> {
                val center = Offset(w * 0.50f, h * 0.50f)
                drawCircle(tint, radius = w * 0.40f, center = center, style = stroke)
                drawLine(tint, Offset(w * 0.10f, h * 0.50f), Offset(w * 0.90f, h * 0.50f), strokeWidth)
                drawLine(tint, Offset(w * 0.50f, h * 0.10f), Offset(w * 0.50f, h * 0.90f), strokeWidth)
            }
            NexoraGlyph.SETTINGS -> {
                val center = Offset(w * 0.50f, h * 0.50f)
                drawCircle(tint, radius = w * 0.24f, center = center, style = stroke)
                for (angle in 0 until 360 step 45) {
                    val rad = Math.toRadians(angle.toDouble())
                    val start = Offset(
                        (center.x + cos(rad) * w * 0.28f).toFloat(),
                        (center.y + sin(rad) * h * 0.28f).toFloat()
                    )
                    val end = Offset(
                        (center.x + cos(rad) * w * 0.42f).toFloat(),
                        (center.y + sin(rad) * h * 0.42f).toFloat()
                    )
                    drawLine(tint, start, end, strokeWidth, StrokeCap.Round)
                }
            }
            NexoraGlyph.CALCULATOR -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.18f, h * 0.12f),
                    size = Size(w * 0.64f, h * 0.76f),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.30f, h * 0.30f), Offset(w * 0.70f, h * 0.30f), strokeWidth, StrokeCap.Round)
                drawCircle(tint, radius = 2.dp.toPx(), center = Offset(w * 0.35f, h * 0.52f))
                drawCircle(tint, radius = 2.dp.toPx(), center = Offset(w * 0.65f, h * 0.52f))
                drawCircle(tint, radius = 2.dp.toPx(), center = Offset(w * 0.35f, h * 0.70f))
                drawCircle(tint, radius = 2.dp.toPx(), center = Offset(w * 0.65f, h * 0.70f))
            }
            NexoraGlyph.QR -> {
                val s = Size(w * 0.28f, h * 0.28f)
                drawRect(tint, Offset(w * 0.14f, h * 0.14f), s, style = stroke)
                drawRect(tint, Offset(w * 0.58f, h * 0.14f), s, style = stroke)
                drawRect(tint, Offset(w * 0.14f, h * 0.58f), s, style = stroke)
                drawCircle(tint, radius = 3.dp.toPx(), center = Offset(w * 0.72f, h * 0.72f))
            }
            NexoraGlyph.NOTES -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.18f, h * 0.14f),
                    size = Size(w * 0.64f, h * 0.72f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.30f, h * 0.34f), Offset(w * 0.70f, h * 0.34f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.50f), Offset(w * 0.70f, h * 0.50f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.66f), Offset(w * 0.54f, h * 0.66f), strokeWidth, StrokeCap.Round)
            }
            NexoraGlyph.FILES -> {
                val path = Path().apply {
                    moveTo(w * 0.14f, h * 0.24f)
                    lineTo(w * 0.40f, h * 0.24f)
                    lineTo(w * 0.48f, h * 0.34f)
                    lineTo(w * 0.86f, h * 0.34f)
                    lineTo(w * 0.86f, h * 0.80f)
                    lineTo(w * 0.14f, h * 0.80f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            NexoraGlyph.TEXT -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.22f), Offset(w * 0.80f, h * 0.22f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.22f), Offset(w * 0.50f, h * 0.82f), strokeWidth, StrokeCap.Round)
            }
            NexoraGlyph.CONVERTER, NexoraGlyph.REFRESH -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.36f), Offset(w * 0.80f, h * 0.36f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.22f), Offset(w * 0.80f, h * 0.36f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.80f, h * 0.64f), Offset(w * 0.20f, h * 0.64f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.78f), Offset(w * 0.20f, h * 0.64f), strokeWidth, StrokeCap.Round)
            }
            NexoraGlyph.TASKS, NexoraGlyph.CHECK -> {
                val path = Path().apply {
                    moveTo(w * 0.20f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.74f)
                    lineTo(w * 0.82f, h * 0.28f)
                }
                drawPath(path, tint, style = stroke)
            }
            NexoraGlyph.SHIELD -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.84f, h * 0.26f)
                    lineTo(w * 0.84f, h * 0.54f)
                    lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.16f, h * 0.54f)
                    lineTo(w * 0.16f, h * 0.26f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            NexoraGlyph.WARNING, NexoraGlyph.ERROR -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.84f)
                    lineTo(w * 0.12f, h * 0.84f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.40f), Offset(w * 0.50f, h * 0.62f), strokeWidth, StrokeCap.Round)
                drawCircle(tint, radius = 2.dp.toPx(), center = Offset(w * 0.50f, h * 0.73f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Reusable Custom Surfaces, Buttons, Inputs & Modals
// ---------------------------------------------------------------------------
@Composable
fun NexoraCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    NexoraCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        borderColor = borderColor,
        contentPadding = contentPadding,
        onClick = onClick,
        content = content
    )
}

@Composable
fun NexoraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    leadingGlyph: NexoraGlyph? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "buttonScale"
    )

    val brush = when {
        !enabled -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
        isPrimary -> Brush.horizontalGradient(
            colors = listOf(NexoraColors.ElectricBlue, NexoraColors.ElectricViolet)
        )
        else -> Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }

    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isPrimary -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(48.dp)
            .clip(NexoraShapes.Control)
            .background(brush)
            .border(
                width = 1.dp,
                color = if (isPrimary && enabled) {
                    NexoraColors.CyberCyan.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = NexoraShapes.Control
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingGlyph != null) {
                NexoraIcon(
                    glyph = leadingGlyph,
                    modifier = Modifier.size(16.dp),
                    tint = textColor
                )
            }
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NeonGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    NexoraButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isPrimary = true
    )
}

@Composable
fun NexoraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    maxLines: Int = 4,
    minLines: Int = 1,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        readOnly = readOnly,
        label = label?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = NexoraShapes.Control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun NexoraSectionHeader(
    eyebrow: String,
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(NexoraShapes.Control)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Loading, Empty, Error & Success States
// ---------------------------------------------------------------------------
@Composable
fun NexoraLoading(
    modifier: Modifier = Modifier,
    label: String = "Synthesizing intelligence..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nexoraLoading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    NexoraCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        )
    }
}

@Composable
fun NexoraEmptyState(
    title: String,
    subtitle: String,
    glyph: NexoraGlyph = NexoraGlyph.ORB,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    NexoraCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outline
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                NexoraIcon(
                    glyph = glyph,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                NexoraButton(
                    text = actionLabel,
                    onClick = onAction,
                    isPrimary = false
                )
            }
        }
    }
}

@Composable
fun NexoraErrorState(
    title: String,
    message: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    NexoraCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = NexoraColors.AmberSignal.copy(alpha = 0.55f),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NexoraIcon(
                glyph = NexoraGlyph.WARNING,
                modifier = Modifier.size(20.dp),
                tint = NexoraColors.AmberSignal
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NexoraColors.AmberSignal
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (retryLabel != null && onRetry != null) {
                NexoraButton(
                    text = retryLabel,
                    onClick = onRetry,
                    isPrimary = false
                )
            }
        }
    }
}

@Composable
fun NexoraSuccessBanner(
    message: String?,
    onDismiss: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        NexoraCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            borderColor = NexoraColors.EmeraldSignal.copy(alpha = 0.55f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            onClick = onDismiss
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NexoraIcon(
                    glyph = NexoraGlyph.CHECK,
                    modifier = Modifier.size(18.dp),
                    tint = NexoraColors.EmeraldSignal
                )
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraColors.EmeraldSignal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun NexoraDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        NexoraCard(
            cornerRadius = 24.dp,
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NexoraButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
                if (confirmText != null && onConfirm != null) {
                    NexoraButton(
                        text = confirmText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        isPrimary = true
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Animated NEXORA AI Orb & Top/Bottom Navigation Bars
// ---------------------------------------------------------------------------
@Composable
fun NexoraAiOrb(
    modifier: Modifier = Modifier.size(136.dp),
    isActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nexoraOrbTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 7500 else 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 11000 else 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbCounterRotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = (size.minDimension / 2f) * 0.72f

        // Outer ambient halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NexoraColors.ElectricViolet.copy(alpha = 0.42f),
                    NexoraColors.CyberCyan.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 1.38f * pulse
            ),
            radius = baseRadius * 1.38f * pulse,
            center = center
        )

        // Primary rotating orbital telemetry ring
        rotate(degrees = rotation, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        NexoraColors.CyberCyan,
                        NexoraColors.ElectricViolet,
                        Color.Transparent,
                        NexoraColors.CyberCyan
                    ),
                    center = center
                ),
                radius = baseRadius * pulse,
                center = center,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Secondary counter-rotating orbital ring
        rotate(degrees = counterRotation, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        NexoraColors.LuminousPurple.copy(alpha = 0.85f),
                        NexoraColors.ElectricBlue,
                        Color.Transparent
                    ),
                    center = center
                ),
                radius = baseRadius * 0.82f,
                center = center,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Inner core sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    NexoraColors.CyberCyan,
                    NexoraColors.ElectricBlue,
                    NexoraColors.ObsidianBlack
                ),
                center = center,
                radius = baseRadius * 0.64f
            ),
            radius = baseRadius * 0.60f,
            center = center
        )
    }
}

@Composable
fun NexoraTopBar(
    title: String,
    subtitle: String,
    isOnline: Boolean,
    timeString: String,
    onOpenTasks: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            NexoraIcon(
                glyph = NexoraGlyph.ORB,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onOpenTasks != null) {
                Box(
                    modifier = Modifier
                        .clip(NexoraShapes.Control)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenTasks)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NexoraIcon(
                            glyph = NexoraGlyph.TASKS,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "TASKS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(NexoraShapes.Pill)
                    .background(
                        if (isOnline) NexoraColors.EmeraldSignal.copy(alpha = 0.14f)
                        else NexoraColors.AmberSignal.copy(alpha = 0.14f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            color = if (isOnline) NexoraColors.EmeraldSignal else NexoraColors.AmberSignal,
                            shape = CircleShape
                        )
                )
                Text(
                    text = if (isOnline) "ONLINE" else "OFFLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOnline) NexoraColors.EmeraldSignal else NexoraColors.AmberSignal
                )
            }
        }
    }
}

data class NexoraNavTabItem(
    val route: String,
    val label: String,
    val glyph: NexoraGlyph
)

@Composable
fun NexoraBottomBar(
    items: List<NexoraNavTabItem>,
    currentRoute: String,
    onSelectRoute: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val tint by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "navTint"
            )
            val bgAlpha by animateFloatAsState(
                targetValue = if (selected) 0.15f else 0f,
                label = "navBgAlpha"
            )
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha))
                    .clickable { onSelectRoute(item.route) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NexoraIcon(glyph = item.glyph, modifier = Modifier.size(20.dp), tint = tint)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. Custom Interactive Charts (Bar Matrix, Spline Area, Radial Gauge, Sparkline)
// ---------------------------------------------------------------------------
@Composable
fun NexoraChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    ProductivityBarChart(values = values, labels = labels, modifier = modifier)
}

@Composable
fun ProductivityBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            val count = values.size.coerceAtLeast(1)
            val slot = size.width / count
            val barWidth = slot * 0.50f

            // Horizontal reference lines
            for (step in 1..3) {
                val yLine = size.height * (step / 4f)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.15f),
                    start = Offset(0f, yLine),
                    end = Offset(size.width, yLine),
                    strokeWidth = 1.dp.toPx()
                )
            }

            values.forEachIndexed { index, value ->
                val ratio = (value / maxVal).coerceIn(0.08f, 1f)
                val barHeight = size.height * ratio
                val x = index * slot + (slot - barWidth) / 2f
                val y = size.height - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(NexoraColors.CyberCyan, NexoraColors.ElectricViolet)
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(10f, 10f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            labels.forEachIndexed { idx, label ->
                val v = values.getOrNull(idx)?.toInt() ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = v.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NexoraSplineAreaChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val safeValues = if (values.size < 2) listOf(1f, 2f) else values
    val maxVal = (safeValues.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            val linePath = Path()
            val fillPath = Path()
            val points = safeValues.mapIndexed { idx, v ->
                val x = (idx.toFloat() / (safeValues.size - 1)) * size.width
                val y = size.height - ((v / maxVal).coerceIn(0.05f, 1f) * (size.height - 20f)) - 10f
                Offset(x, y)
            }

            points.forEachIndexed { idx, pt ->
                if (idx == 0) {
                    linePath.moveTo(pt.x, pt.y)
                    fillPath.moveTo(pt.x, size.height)
                    fillPath.lineTo(pt.x, pt.y)
                } else {
                    val prev = points[idx - 1]
                    val ctrlX = (prev.x + pt.x) / 2f
                    linePath.cubicTo(ctrlX, prev.y, ctrlX, pt.y, pt.x, pt.y)
                    fillPath.cubicTo(ctrlX, prev.y, ctrlX, pt.y, pt.x, pt.y)
                }
            }
            fillPath.lineTo(points.last().x, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NexoraColors.CyberCyan.copy(alpha = 0.32f),
                        NexoraColors.ElectricViolet.copy(alpha = 0.04f)
                    )
                )
            )
            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(NexoraColors.CyberCyan, NexoraColors.LuminousPurple)
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            points.forEach { pt ->
                drawCircle(
                    color = NexoraColors.CyberCyan,
                    radius = 4.dp.toPx(),
                    center = pt
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NexoraRadialGauge(
    percentage: Int,
    label: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    val clamped = percentage.coerceIn(0, 100)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(104.dp)) {
                val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = Color.Gray.copy(alpha = 0.2f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = stroke
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            NexoraColors.CyberCyan,
                            NexoraColors.ElectricViolet,
                            NexoraColors.CyberCyan
                        )
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * (clamped / 100f),
                    useCenter = false,
                    style = stroke
                )
            }
            Text(
                text = "$clamped%",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LatencySparklineChart(
    rttSamples: List<Long>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        if (rttSamples.size < 2) return@Canvas
        val validSamples = rttSamples.map { if (it < 0) 250f else it.toFloat() }
        val maxVal = (validSamples.maxOrNull() ?: 100f).coerceAtLeast(20f)
        val minVal = (validSamples.minOrNull() ?: 0f).coerceAtLeast(0f)
        val range = (maxVal - minVal).coerceAtLeast(10f)

        val path = Path()
        validSamples.forEachIndexed { idx, sample ->
            val x = (idx.toFloat() / (validSamples.size - 1)) * size.width
            val normalizedY = 1f - ((sample - minVal) / range)
            val y = (normalizedY * (size.height - 16f)) + 8f
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(NexoraColors.CyberCyan, NexoraColors.LuminousPurple)
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
