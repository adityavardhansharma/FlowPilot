@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.flowpilot.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flowpilot.app.R
import dev.flowpilot.app.data.LinkState
import dev.flowpilot.core.chat.ToolIcon
import kotlinx.coroutines.delay

/** Material Symbols Rounded, shipped as vector drawables. */
object Ic {
    val send = R.drawable.ic_send
    val stop = R.drawable.ic_stop
    val arrowUp = R.drawable.ic_arrow_upward
    val add = R.drawable.ic_add
    val search = R.drawable.ic_search
    val close = R.drawable.ic_close
    val back = R.drawable.ic_arrow_back
    val more = R.drawable.ic_more_vert
    val qr = R.drawable.ic_qr_code_scanner
    val chat = R.drawable.ic_chat
    val folder = R.drawable.ic_folder
    val folderOpen = R.drawable.ic_folder_open
    val newFolder = R.drawable.ic_create_new_folder
    val inbox = R.drawable.ic_inbox
    val settings = R.drawable.ic_settings
    val check = R.drawable.ic_check
    val checkCircle = R.drawable.ic_check_circle
    val error = R.drawable.ic_error
    val warning = R.drawable.ic_warning
    val expandMore = R.drawable.ic_expand_more
    val expandLess = R.drawable.ic_expand_less
    val chevronRight = R.drawable.ic_chevron_right
    val copy = R.drawable.ic_content_copy
    val terminal = R.drawable.ic_terminal
    val psychology = R.drawable.ic_psychology
    val schedule = R.drawable.ic_schedule
    val jump = R.drawable.ic_keyboard_double_arrow_down
    val refresh = R.drawable.ic_refresh
    val delete = R.drawable.ic_delete
    val pin = R.drawable.ic_push_pin
    val dns = R.drawable.ic_dns
    val link = R.drawable.ic_link
    val visibility = R.drawable.ic_visibility
    val visibilityOff = R.drawable.ic_visibility_off
    val download = R.drawable.ic_download
    val build = R.drawable.ic_build
    val checklist = R.drawable.ic_checklist
    val tune = R.drawable.ic_tune
    val cancel = R.drawable.ic_cancel
    val wifiOff = R.drawable.ic_wifi_off
    val autoAwesome = R.drawable.ic_auto_awesome
    val info = R.drawable.ic_info
    val history = R.drawable.ic_history
    val stopCircle = R.drawable.ic_stop_circle
    val editSquare = R.drawable.ic_edit_square
    val dropDown = R.drawable.ic_arrow_drop_down
    val markUnread = R.drawable.ic_mark_chat_unread
    val block = R.drawable.ic_block
    val doneAll = R.drawable.ic_done_all
    val image = R.drawable.ic_image
    val unfoldMore = R.drawable.ic_unfold_more
    val unfoldLess = R.drawable.ic_unfold_less
    val sync = R.drawable.ic_sync
    val logout = R.drawable.ic_logout
    val lock = R.drawable.ic_lock
    val keyboard = R.drawable.ic_keyboard
    val subdirectory = R.drawable.ic_subdirectory_arrow_right
    val radioOn = R.drawable.ic_radio_button_checked
    val radioOff = R.drawable.ic_radio_button_unchecked
    val boxOn = R.drawable.ic_check_box
    val boxOff = R.drawable.ic_check_box_outline_blank
    val openInNew = R.drawable.ic_open_in_new
    val tree = R.drawable.ic_account_tree

    fun tool(icon: ToolIcon): Int = when (icon) {
        ToolIcon.Read -> R.drawable.ic_description
        ToolIcon.Search -> R.drawable.ic_manage_search
        ToolIcon.Glob -> R.drawable.ic_folder_open
        ToolIcon.Edit -> R.drawable.ic_edit
        ToolIcon.Patch -> R.drawable.ic_difference
        ToolIcon.Write -> R.drawable.ic_edit_note
        ToolIcon.Shell -> R.drawable.ic_terminal
        ToolIcon.WebSearch -> R.drawable.ic_travel_explore
        ToolIcon.WebFetch -> R.drawable.ic_language
        ToolIcon.Browser -> R.drawable.ic_public
        ToolIcon.Subagent -> R.drawable.ic_smart_toy
        ToolIcon.Skill -> R.drawable.ic_bolt
        ToolIcon.Question -> R.drawable.ic_help
        ToolIcon.Mcp -> R.drawable.ic_extension
    }
}

@Composable
fun Sym(@DrawableRes id: Int, contentDescription: String? = null, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, size: Dp = 24.dp) {
    Icon(
        painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
    )
}

/** Where the phone stands with the computer. Reconnecting only shows after 2s, so blips stay invisible. */
@Composable
fun ServerChip(name: String, state: LinkState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var showTrouble by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        showTrouble = false
        if (state == LinkState.Reconnecting || state == LinkState.Connecting) { delay(2000); showTrouble = true }
        if (state == LinkState.Unauthorized) showTrouble = true
    }
    val trouble = showTrouble && state != LinkState.Online
    val container = when {
        trouble && state == LinkState.Unauthorized -> MaterialTheme.colorScheme.errorContainer
        trouble -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(onClick = onClick, shape = CircleShape, color = container, modifier = modifier.height(36.dp)) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (trouble && state != LinkState.Unauthorized) {
                val t = rememberInfiniteTransition(label = "sync")
                val angle by t.animateFloat(0f, -360f, infiniteRepeatable(tween(1200)), label = "angle")
                Sym(Ic.sync, null, Modifier.rotate(angle), size = 18.dp)
            } else if (trouble) {
                Sym(Ic.lock, null, size = 18.dp)
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    trouble && state == LinkState.Unauthorized -> "Pair again"
                    trouble -> "Reconnecting"
                    else -> name
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
    }
}

private val projectShapes by lazy {
    listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pill,
        MaterialShapes.Gem, MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.Puffy,
        MaterialShapes.Pentagon, MaterialShapes.SoftBurst, MaterialShapes.Arch, MaterialShapes.Cookie12Sided,
    )
}

/** Each project gets a stable shape and tone from its name, so it's recognisable at a glance. */
@Composable
fun ProjectShape(name: String, size: Dp = 40.dp, muted: Boolean = false) {
    val h = (name.hashCode() and 0x7fffffff)
    val shape = remember(name) { projectShapes[h % projectShapes.size].toShape() }
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        muted -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        h % 3 == 0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        h % 3 == 1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceContainerHighest to scheme.onSurface
    }
    Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·",
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** One line of what this place is for and one action. No illustration. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    @DrawableRes icon: Int? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Sym(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, size = 32.dp) }
            Spacer(Modifier.height(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(action) }
        }
        if (secondary != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondary) }
        }
    }
}

/** An inline, recoverable error at the place of failure. */
@Composable
fun ErrorCard(title: String, body: String?, modifier: Modifier = Modifier, action: String? = "Retry", onAction: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym(Ic.error, null, size = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (!body.isNullOrBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
fun LoadingRow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(36.dp)) }
}

@Composable
fun CenteredLoading(slowHint: Boolean = true) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(10_000); slow = true }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        LoadingIndicator(Modifier.size(56.dp))
        if (slow && slowHint) {
            Spacer(Modifier.height(16.dp))
            Text("Still loading. Your computer may be busy.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Skeleton row at the real ThreadRow size, pulsing gently. */
@Composable
fun SkeletonRow() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(1f, 0.6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp).alpha(a), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
}

/** Section header used by lists and sheets. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A rounded group container for list rows, like the segmented lists in the design system. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column { content() }
    }
}

@Composable
fun ClickRow(onClick: () -> Unit, modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clickable(onClick = onClick).padding(padding)) { content() }
}
