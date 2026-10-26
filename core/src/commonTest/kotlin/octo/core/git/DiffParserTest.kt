package octo.core.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiffParserTest {
    private val sample =
        """
        diff --git a/src/app.kt b/src/app.kt
        index 1111111..2222222 100644
        --- a/src/app.kt
        +++ b/src/app.kt
        @@ -1,3 +1,4 @@ fun main() {
         line one
        -line two
        +line 2
        +line 3

        diff --git a/docs/read me.md b/docs/read me.md
        new file mode 100644
        index 0000000..3333333
        --- /dev/null
        +++ b/docs/read me.md
        @@ -0,0 +1 @@
        +hello
        \ No newline at end of file
        diff --git a/old.txt b/old.txt
        deleted file mode 100644
        index 4444444..0000000
        --- a/old.txt
        +++ /dev/null
        @@ -1 +0,0 @@
        -bye
        diff --git a/a.txt b/b.txt
        similarity index 90%
        rename from a.txt
        rename to b.txt
        diff --git a/logo.png b/logo.png
        index 5555555..6666666 100644
        Binary files a/logo.png and b/logo.png differ
        """.trimIndent()

    @Test
    fun parsesEveryKindOfChange() {
        val files = DiffParser.parse(sample)
        assertEquals(listOf("src/app.kt", "docs/read me.md", "old.txt", "b.txt", "logo.png"), files.map { it.path })
        assertEquals(
            listOf(FileChange.Modified, FileChange.Added, FileChange.Deleted, FileChange.Renamed, FileChange.Modified),
            files.map { it.change },
        )

        val modified = files[0]
        assertEquals(2 to 1, modified.additions to modified.deletions)
        val hunk = modified.hunks.single()
        assertEquals(listOf(1, 3, 1, 4), listOf(hunk.oldStart, hunk.oldCount, hunk.newStart, hunk.newCount))
        assertEquals("fun main() {", hunk.section)
        assertEquals(
            listOf(LineKind.Context, LineKind.Removed, LineKind.Added, LineKind.Added, LineKind.Context),
            hunk.lines.map { it.kind },
        )

        assertEquals(listOf(DiffLine(LineKind.Added, "hello")), files[1].hunks.single().lines)
        assertEquals(listOf(DiffLine(LineKind.Removed, "bye")), files[2].hunks.single().lines)
        assertEquals("a.txt", files[3].oldPath)
        assertTrue(files[4].binary)
    }

    @Test
    fun ignoresGarbage() {
        assertEquals(emptyList(), DiffParser.parse(""))
        assertEquals(emptyList(), DiffParser.parse("not a diff\n@@ nonsense @@"))
        val truncated = DiffParser.parse("diff --git a/x b/x\n@@ -1,5 +1,5 @@\n-only")
        assertEquals(1, truncated.single().deletions)
    }
}
