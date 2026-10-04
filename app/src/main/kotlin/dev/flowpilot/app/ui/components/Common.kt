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
import dev.flowpilot.app.ui.theme.Fp
import dev.flowpilot.app.ui.theme.FpType
import dev.flowpilot.app.ui.theme.Radius
import dev.flowpilot.app.ui.theme.pressable
import dev.flowpilot.app.ui.theme.pressed
import dev.flowpilot.app.ui.theme.raised
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
    val file = R.drawable.ic_description
    val bolt = R.drawable.ic_bolt
    val slash = R.drawable.ic_slash

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
        if (state != LinkState.Online && state != LinkState.Unauthorized) { delay(2000); showTrouble = true }
        if (state == LinkState.Unauthorized || state == LinkState.Unsupported) showTrouble = true
    }
    val trouble = showTrouble && state != LinkState.Online
    val c = Fp.colors
    val container = when {
        trouble && state == LinkState.Unauthorized -> c.dangerSoft
        else -> c.surface
    }
    val ink = if (trouble && state == LinkState.Unauthorized) c.onDangerSoft else c.ink
    Row(modifier.height(36.dp).pressable(Radius.full, container, onClick).padding(start = 12.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        OnSurface(ink) {
            if (trouble && state != LinkState.Unauthorized) {
                val t = rememberInfiniteTransition(label = "sync")
                val angle by t.animateFloat(0f, -360f, infiniteRepeatable(tween(1200)), label = "angle")
                Sym(Ic.sync, null, Modifier.rotate(angle), size = 18.dp)
            } else if (trouble) {
                Sym(Ic.lock, null, size = 18.dp)
            } else {
                StillDot(c.success)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    trouble && state == LinkState.Unauthorized -> "Pair again"
                    trouble && state == LinkState.Unsupported -> "Unsupported server"
                    trouble && state == LinkState.CatchingUp -> "Catching up"
                    trouble && state == LinkState.Offline -> "Offline"
                    trouble -> "Reconnecting"
                    else -> name
                },
                style = FpType.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
    }
}

/** Each project is a pebble with its initial: FlowPilot's own shape, recognisable at a glance. */
@Composable
fun ProjectShape(name: String, size: Dp = 40.dp, muted: Boolean = false) = Pebble(name, size, muted = muted)

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
                Modifier.size(72.dp).pressed(PebbleShape, Fp.colors.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) { Sym(icon, null, tint = Fp.colors.inkMuted, size = 30.dp) }
            Spacer(Modifier.height(20.dp))
        }
        Text(title, style = FpType.titleLg, textAlign = TextAlign.Center, color = Fp.colors.ink)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = FpType.body, color = Fp.colors.inkMuted, textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            FpButton(action, onAction, variant = FpButtonVariant.Primary)
        }
        if (secondary != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            FpButton(secondary, onSecondary, variant = FpButtonVariant.Ghost)
        }
    }
}

/** An inline, recoverable error at the place of failure: danger-soft, an alert icon, one action. */
@Composable
fun ErrorCard(title: String, body: String?, modifier: Modifier = Modifier, action: String? = "Retry", onAction: (() -> Unit)? = null) {
    val c = Fp.colors
    Row(modifier.fillMaxWidth().raised(Radius.lg, c.dangerSoft).padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(Ic.error, null, size = 20.dp, tint = c.onDangerSoft)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FpType.label.copy(fontWeight = FontWeight(600)), color = c.onDangerSoft)
            if (!body.isNullOrBlank()) Text(body, style = FpType.body, color = c.onDangerSoft, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (action != null && onAction != null) { Spacer(Modifier.width(8.dp)); FpButton(action, onAction, small = true) }
    }
}

@Composable
fun LoadingRow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { FpSpinner(Modifier.size(36.dp)) }
}

@Composable
fun CenteredLoading(slowHint: Boolean = true) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(10_000); slow = true }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FpSpinner(Modifier.size(56.dp))
        if (slow && slowHint) {
            Spacer(Modifier.height(16.dp))
            Text("Still loading. Your computer may be busy.", style = FpType.body, color = Fp.colors.inkMuted)
        }
    }
}

/**
 * Skeleton row at the real ThreadRow size, pulsing between full and 55% opacity at duration-shimmer. Its pebble
 * and bars are wells, the same as what they stand in for.
 */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier, widths: Pair<Float, Float> = 0.62f to 0.38f) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(1f, 0.55f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    val well = Fp.colors.surfaceSunken
    Row(modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp).alpha(a), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(PebbleShape).background(well))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(widths.first).height(13.dp).clip(CircleShape).background(well))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(widths.second).height(10.dp).clip(CircleShape).background(well))
        }
    }
}

/** Section header used by lists and sheets: an overline in capitals. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = Fp.colors.inkMuted, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = FpType.overline, color = color, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Rows that belong together share one raised card on the ground, split by hairlines. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).raised(Radius.lg, Fp.colors.surface)) {
        OnSurface { content() }
    }
}

@Composable
fun ClickRow(onClick: () -> Unit, modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clickable(onClick = onClick).padding(padding)) { content() }
}
