package dev.flowpilot.core.chat

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String, val code: String, val closed: Boolean) : MdBlock
    data class Bullets(val ordered: Boolean, val start: Int, val items: List<Item>) : MdBlock {
        data class Item(val text: String, val depth: Int, val checked: Boolean?)
    }
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}


/**
 * A small, forgiving CommonMark subset that's safe to re-run on every streamed delta:
 * an unclosed fence renders as an open code block, a half list stays a list.
 */
object MarkdownParser {
    private val fence = Regex("^\\s{0,3}(```+|~~~+)\\s*([\\w+#.-]*)")
    private val heading = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val bullet = Regex("^(\\s*)([-*+])\\s+(\\[[ xX]]\\s+)?(.*)$")
    private val ordered = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val rule = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val tableSep = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun parse(src: String): List<MdBlock> {
        val lines = src.replace("\r\n", "\n").split('\n')
        val out = ArrayList<MdBlock>()
        val para = StringBuilder()
        fun flushPara() {
            if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
            para.clear()
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val f = fence.find(line)
            if (f != null) {
                flushPara()
                val marker = f.groupValues[1]
                val lang = f.groupValues[2]
                val body = StringBuilder()
                i++
                var closed = false
                while (i < lines.size) {
                    if (lines[i].trimStart().startsWith(marker)) { closed = true; i++; break }
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[i])
                    i++
                }
                out += MdBlock.Code(lang, body.toString(), closed)
                continue
            }
            if (line.isBlank()) { flushPara(); i++; continue }
            val h = heading.find(line)
            if (h != null) {
                flushPara(); out += MdBlock.Heading(h.groupValues[1].length, h.groupValues[2]); i++
                continue
            }
            if (rule.matches(line) && para.isEmpty()) { out += MdBlock.Rule; i++; continue }
            if (line.contains('|') && i + 1 < lines.size && tableSep.matches(lines[i + 1])) {
                flushPara()
                val header = cells(line)
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) { rows += cells(lines[i]); i++ }
                out += MdBlock.Table(header, rows)
                continue
            }
            if (line.trimStart().startsWith(">")) {
                flushPara()
                val q = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    q.append(lines[i].trimStart().removePrefix(">").removePrefix(" ")).append('\n'); i++
                }
                out += MdBlock.Quote(q.toString().trim())
                continue
            }
            val b = bullet.find(line)
            val o = ordered.find(line)
            if (b != null || o != null) {
                flushPara()
                val isOrdered = b == null
                val start = o?.groupValues?.get(2)?.toIntOrNull() ?: 1
                val items = ArrayList<MdBlock.Bullets.Item>()
                while (i < lines.size) {
                    val l = lines[i]
                    val bb = bullet.find(l)
                    val oo = ordered.find(l)
                    val depth0 = (bb ?: oo)?.groupValues?.get(1)?.length == 0
                    if (depth0 && ((bb != null && isOrdered) || (oo != null && !isOrdered))) break
                    when {
                        bb != null -> items += MdBlock.Bullets.Item(
                            bb.groupValues[4], bb.groupValues[1].length / 2,
                            bb.groupValues[3].takeIf { it.isNotEmpty() }?.let { it.contains('x', ignoreCase = true) },
                        )
                        oo != null -> items += MdBlock.Bullets.Item(oo.groupValues[3], oo.groupValues[1].length / 2, null)
                        l.isNotBlank() && l.startsWith("  ") && items.isNotEmpty() ->
                            items[items.lastIndex] = items.last().copy(text = items.last().text + " " + l.trim())
                        else -> break
                    }
                    i++
                }
                out += MdBlock.Bullets(isOrdered, start, items)
                continue
            }
            if (para.isNotEmpty()) para.append('\n')
            para.append(line)
            i++
        }
        flushPara()
        return out
    }

    private fun cells(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
}

/** Settled blocks are parsed once. Only the unfinished tail is reparsed while text grows. */
class MarkdownStreamParser {
    private var previous = ""
    private var scanned = 0
    private var settledEnd = 0
    private var fence: String? = null
    private val settled = ArrayList<MdBlock>()
    private var result: List<MdBlock> = emptyList()
    private val opening = Regex("^\\s{0,3}(```+|~~~+)\\s*([\\w+#.-]*)")

    /** The blocks from the most recent [parse], or none before the first. */
    val latest: List<MdBlock>
        @Synchronized get() = result

    @Synchronized fun parse(raw: String): List<MdBlock> {
        val source = raw.replace("\r\n", "\n")
        if (source == previous) return result
        if (!source.startsWith(previous)) {
            scanned = 0; settledEnd = 0; fence = null; settled.clear()
        }
        var boundary = settledEnd
        while (true) {
            val end = source.indexOf('\n', scanned)
            if (end < 0) break
            val line = source.substring(scanned, end)
            val marker = fence
            if (marker != null) {
                if (line.trimStart().startsWith(marker)) fence = null
            } else {
                val start = opening.find(line)
                if (start != null) fence = start.groupValues[1]
                else if (line.isBlank()) boundary = end + 1
            }
            scanned = end + 1
        }
        if (boundary > settledEnd) {
            settled += MarkdownParser.parse(source.substring(settledEnd, boundary))
            settledEnd = boundary
        }
        result = settled.toList() + MarkdownParser.parse(source.substring(settledEnd))
        previous = source
        return result
    }
}
