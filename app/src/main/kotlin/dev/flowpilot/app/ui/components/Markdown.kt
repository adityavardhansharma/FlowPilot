package dev.flowpilot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.flowpilot.app.ui.theme.CodeFamily
import dev.flowpilot.core.chat.MarkdownParser
import dev.flowpilot.core.chat.MdBlock
import dev.flowpilot.app.ui.theme.CodeStyle
import dev.flowpilot.app.ui.theme.FlowPilotColors
import dev.flowpilot.app.ui.theme.code

object Markdown {
    private val inline = Regex(
        "(`+)(.+?)\\1" +                                  // code
            "|\\*\\*(.+?)\\*\\*|__(.+?)__" +              // bold
            "|(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])|(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])" + // italic
            "|~~(.+?)~~" +                                 // strike
            "|\\[([^\\]]+)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)" + // link
            "|(https?://[^\\s<>()\\[\\]]+[^\\s<>()\\[\\].,;:!?'\"])", // bare url
    )

    fun inline(text: String, colors: FlowPilotColors, link: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
        appendInline(this, text, colors, link, codeBg)
    }

    private fun appendInline(b: AnnotatedString.Builder, text: String, colors: FlowPilotColors, link: Color, codeBg: Color) {
        var last = 0
        for (m in inline.findAll(text)) {
            b.append(text.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[2].isNotEmpty() -> b.withStyle(SpanStyle(fontFamily = CodeFamily, background = codeBg, color = colors.codeInk)) { append(" " + g[2].trim() + " ") }
                g[3].isNotEmpty() || g[4].isNotEmpty() -> b.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInline(this, g[3].ifEmpty { g[4] }, colors, link, codeBg) }
                g[5].isNotEmpty() || g[6].isNotEmpty() -> b.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(this, g[5].ifEmpty { g[6] }, colors, link, codeBg) }
                g[7].isNotEmpty() -> b.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[7]) }
                g[8].isNotEmpty() -> b.withLink(LinkAnnotation.Url(g[9], TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))) { append(g[8]) }
                g[10].isNotEmpty() -> b.withLink(LinkAnnotation.Url(g[10], TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))) { append(g[10]) }
                else -> b.append(m.value)
            }
            last = m.range.last + 1
        }
        b.append(text.substring(last))
    }
}

/** Renders assistant markdown. [streaming] adds the caret after the last block. */
@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier, streaming: Boolean = false, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val parser = remember { dev.flowpilot.core.chat.MarkdownStreamParser() }
    val blocks by androidx.compose.runtime.produceState<List<dev.flowpilot.core.chat.MdBlock>>(emptyList(), source) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { parser.parse(source) }
    }
    val colors = MaterialTheme.code
    val link = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val caretColor = MaterialTheme.colorScheme.primary
    fun inl(s: String, caret: Boolean) = Markdown.inline(s, colors, link, codeBg).let { a ->
        if (!caret) a else buildAnnotatedString { append(a); withStyle(SpanStyle(color = caretColor)) { append(" ▍") } }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { index, block ->
            val caret = streaming && index == blocks.lastIndex
            when (block) {
                is MdBlock.Paragraph -> Text(inl(block.text, caret), style = style)
                is MdBlock.Heading -> Text(
                    inl(block.text, caret),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                is MdBlock.Code -> CodeBlock(block.code, block.lang, streaming = caret && !block.closed)
                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { n, item ->
                        Row(Modifier.padding(start = (item.depth * 16).dp)) {
                            val marker = when {
                                item.checked == true -> "☑"
                                item.checked == false -> "☐"
                                block.ordered -> "${block.start + n}."
                                item.depth > 0 -> "◦"
                                else -> "•"
                            }
                            Text(marker, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(inl(item.text, caret && n == block.items.lastIndex), style = style, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(Modifier.width(12.dp))
                    Text(inl(block.text, caret), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                is MdBlock.Table -> MdTable(block) { inl(it, false) }
            }
        }
    }
}

@Composable
private fun MdTable(t: MdBlock.Table, inl: (String) -> AnnotatedString) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp)) {
            val cols = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0)
            for (c in 0 until cols) {
                Column {
                    Text(inl(t.header.getOrElse(c) { "" }), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(min = 48.dp, max = 260.dp))
                    t.rows.forEach { r ->
                        Text(inl(r.getOrElse(c) { "" }), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).widthIn(min = 48.dp, max = 260.dp))
                    }
                }
            }
        }
    }
}

/** A fenced code well with a language label and copy. Highlighting runs per line. */
@Composable
fun CodeBlock(code: String, lang: String = "", modifier: Modifier = Modifier, streaming: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    val colors = MaterialTheme.code
    val clipboard = LocalClipboardManager.current
    val highlighted = remember(code, lang, colors) { Highlighter.highlight(code, lang, colors) }
    Surface(color = colors.codeBg, contentColor = colors.codeInk, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(lang.ifEmpty { "code" }, style = MaterialTheme.typography.labelMedium, color = colors.codeComment, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Sym(Ic.copy, "Copy code", size = 18.dp, tint = colors.codeComment) }
            }
            SelectionContainer {
                Text(
                    highlighted,
                    style = CodeStyle,
                    softWrap = false,
                    maxLines = maxLines,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

object Highlighter {
    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "do", "return", "import", "package",
        "private", "public", "internal", "protected", "override", "data", "sealed", "enum", "const", "suspend", "null", "true", "false",
        "function", "const", "let", "new", "this", "async", "await", "export", "default", "from", "type", "extends", "implements",
        "def", "self", "None", "True", "False", "elif", "in", "not", "and", "or", "is", "lambda", "try", "catch", "except", "finally",
        "throw", "throws", "static", "void", "int", "string", "bool", "struct", "impl", "pub", "mut", "match", "use", "mod", "go", "func",
        "package", "switch", "case", "break", "continue", "yield", "with", "as", "echo", "then", "fi", "done", "esac",
    )
    private val token = Regex(
        "(//[^\\n]*|#(?![!{\\[])[^\\n]*|/\\*[\\s\\S]*?\\*/)" + // comments
            "|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`)" + // strings
            "|\\b(\\d+(?:\\.\\d+)?[a-zA-Z]*)\\b" +          // numbers
            "|\\b([A-Za-z_][A-Za-z0-9_]*)(\\s*\\()?",       // identifiers, maybe calls
    )

    fun highlight(code: String, lang: String, c: FlowPilotColors): AnnotatedString = buildAnnotatedString {
        if (code.length > 40_000 || lang in setOf("text", "txt", "diff", "log", "output")) { append(code); return@buildAnnotatedString }
        val hashComments = lang in setOf("py", "python", "sh", "bash", "zsh", "shell", "yaml", "yml", "toml", "rb", "ruby", "")
        var last = 0
        for (m in token.findAll(code)) {
            append(code.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() && (hashComments || !g[1].startsWith("#")) -> withStyle(SpanStyle(color = c.codeComment, fontStyle = FontStyle.Italic)) { append(g[1]) }
                g[1].isNotEmpty() -> append(g[1])
                g[2].isNotEmpty() -> withStyle(SpanStyle(color = c.codeString)) { append(g[2]) }
                g[3].isNotEmpty() -> withStyle(SpanStyle(color = c.codeNumber)) { append(g[3]) }
                g[4].isNotEmpty() -> {
                    val style = when {
                        g[4] in keywords -> SpanStyle(color = c.codeKeyword)
                        g[5].isNotEmpty() -> SpanStyle(color = c.codeFunction)
                        else -> null
                    }
                    if (style != null) withStyle(style) { append(g[4]) } else append(g[4])
                    append(g[5])
                }
                else -> append(m.value)
            }
            last = m.range.last + 1
        }
        append(code.substring(last))
    }
}

/** Unified diff with add and remove tints. */
@Composable
fun DiffView(patch: String, modifier: Modifier = Modifier, maxLines: Int = 400) {
    val c = MaterialTheme.code
    val lines = remember(patch) {
        patch.lines().filterNot { it.startsWith("diff --git") || it.startsWith("index ") || it.startsWith("---") || it.startsWith("+++") }.take(maxLines)
    }
    Surface(color = c.codeBg, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            lines.forEach { l ->
                val (bg, fg) = when {
                    l.startsWith("@@") -> Color.Transparent to c.codeComment
                    l.startsWith("+") -> c.diffAddBg to c.diffAddInk
                    l.startsWith("-") -> c.diffRemoveBg to c.diffRemoveInk
                    else -> Color.Transparent to c.codeInk
                }
                Text(
                    l.ifEmpty { " " },
                    style = CodeStyle,
                    color = fg,
                    softWrap = false,
                    modifier = Modifier.background(bg).padding(horizontal = 12.dp).widthIn(min = 360.dp),
                )
            }
        }
    }
}
