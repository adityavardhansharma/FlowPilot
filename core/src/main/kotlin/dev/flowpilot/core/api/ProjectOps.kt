package dev.flowpilot.core.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Folder creation, git init and clone, built from fs.write and the shell API (v2 has no clone route). */
class ProjectOps(private val client: OpenCodeClient) {

    /** The server user's home folder, read once through the shell. */
    suspend fun homeDir(): String {
        val out = run("printf %s \"\$HOME\"", "/")
        return out.output.trim().ifEmpty { "/" }
    }

    /** `~/FlowPilot/Scratch`, created on demand. Chats started with "No project" live here. */
    suspend fun scratchDir(home: String): String {
        val dir = "${home.trimEnd('/')}/FlowPilot/Scratch"
        client.writeFile(home, "FlowPilot/Scratch/.gitkeep", "")
        return dir
    }

    /** Creates [parent]/[name] by writing [name]/.gitkeep from [parent] (fs.write needs an existing location but creates subfolders), then optionally runs git init. */
    suspend fun createFolder(parent: String, name: String, gitInit: Boolean): String {
        val clean = name.trim().replace(Regex("[/\\\\]"), "-")
        require(clean.isNotEmpty() && clean != "." && clean != "..") { "Pick a folder name." }
        val dir = "${parent.trimEnd('/')}/$clean"
        client.writeFile(parent, "$clean/.gitkeep", "")
        if (gitInit) {
            val r = run("git init -q", dir)
            if (r.exit != 0) throw IllegalStateException("git init failed: ${r.output.take(300)}")
        }
        return dir
    }

    sealed interface CloneProgress {
        data class Running(val line: String, val percent: Int?) : CloneProgress
        data class Done(val directory: String) : CloneProgress
        data class Failed(val output: String) : CloneProgress
    }

    @kotlinx.serialization.Serializable
    data class CloneJob(val shellID: String, val directory: String, val cwd: String)

    /** Reattaches to a persisted shell job; navigation only cancels polling, never starts another clone. */
    fun clone(
        url: String,
        parent: String,
        folder: String? = null,
        resume: CloneJob? = null,
        onStarted: suspend (CloneJob) -> Unit = {},
    ): Flow<CloneProgress> = flow {
        val name = folder?.takeIf { it.isNotBlank() } ?: repoName(url)
        require(name != "." && name != ".." && '/' !in name && '\\' !in name) { "Pick a single folder name." }
        val dir = "${parent.trimEnd('/')}/$name"
        val job = resume ?: run {
            val cmd = "git clone --progress -- ${shellQuote(url)} ${shellQuote(name)} 2>&1"
            val shell = client.startShell(cmd, parent, timeoutMs = 30 * 60 * 1000)
            CloneJob(shell.id, dir, parent).also { onStarted(it) }
        }
        var cursor = 0L
        val tail = StringBuilder()
        while (true) {
            val info = client.shell(job.shellID, job.cwd)
            do {
                val out = client.shellOutput(job.shellID, job.cwd, cursor)
                val advanced = out.cursor > cursor
                if (out.output.isNotEmpty()) {
                    tail.append(out.output)
                    if (tail.length > 8192) tail.delete(0, tail.length - 8192)
                    val line = tail.toString().split('\r', '\n').lastOrNull { it.isNotBlank() }.orEmpty()
                    emit(CloneProgress.Running(line, percentOf(line)))
                }
                cursor = maxOf(cursor, out.cursor)
                // Once exited, drain every remaining output page before reporting the result.
            } while (!info.running && advanced && out.output.isNotEmpty())
            if (!info.running) {
                if ((info.exit ?: 1.0) == 0.0) emit(CloneProgress.Done(job.directory))
                else emit(CloneProgress.Failed(tail.takeLast(2000).toString()))
                return@flow
            }
            delay(400)
        }
    }

    data class RunResult(val exit: Int, val output: String)

    /** Runs a short command and waits for it. */
    suspend fun run(command: String, cwd: String, timeoutMs: Long = 60_000): RunResult {
        val shell = client.startShell(command, cwd, timeoutMs.toInt())
        val deadline = System.nanoTime() / 1_000_000 + timeoutMs
        var info = shell
        while (info.running && System.nanoTime() / 1_000_000 < deadline) {
            delay(150)
            info = client.shell(shell.id, cwd)
        }
        val out = client.shellOutput(shell.id, cwd)
        return RunResult(info.exit?.toInt() ?: -1, out.output)
    }

    companion object {
        fun repoName(url: String): String =
            url.trim().trimEnd('/').substringAfterLast('/').substringAfterLast(':').removeSuffix(".git").ifEmpty { "repo" }

        fun percentOf(line: String): Int? = Regex("(\\d{1,3})%").findAll(line).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()

        fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

        /** Accepts https, ssh and "owner/repo" shorthand for GitHub. */
        fun normalizeRepoUrl(input: String): String? {
            val t = input.trim()
            return when {
                t.isEmpty() -> null
                t.startsWith("http://") || t.startsWith("https://") || t.startsWith("git@") || t.startsWith("ssh://") -> t
                Regex("^[\\w.-]+/[\\w.-]+$").matches(t) -> "https://github.com/$t.git"
                t.startsWith("github.com/") -> "https://$t"
                else -> null
            }
        }
    }
}
