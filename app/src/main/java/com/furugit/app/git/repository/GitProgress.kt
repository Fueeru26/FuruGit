package com.furugit.app.git.repository

import com.furugit.app.core.common.ActivityLog
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.TextProgressMonitor

/** Progres operasi JGit yang ditulis ke log aktivitas. */
object GitProgress {

    /** Menulis baris progres JGit ke log aktivitas. */
    private class LogWriter : java.io.Writer() {
        private val buf = StringBuilder()

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            buf.append(cbuf, off, len)
            var i = buf.indexOf("\n")
            while (i >= 0) {
                val line = buf.substring(0, i).substringAfterLast('\r').trim()
                if (line.isNotEmpty()) ActivityLog.add("   · $line")
                buf.delete(0, i + 1)
                i = buf.indexOf("\n")
            }
        }

        override fun flush() {}
        override fun close() {}
    }

    fun monitor(): ProgressMonitor = TextProgressMonitor(LogWriter())
}
