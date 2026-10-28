package octo.ui.preview

import androidx.compose.ui.graphics.ImageBitmap

/** What the diff view shows in place of a file's diff. */
sealed interface Preview {
    class Picture(
        val bitmap: ImageBitmap,
    ) : Preview

    data class Notebook(
        val cells: List<Cell>,
    ) : Preview

    /** The first [entries] of an archive that holds [total]. */
    data class Archive(
        val entries: List<Entry>,
        val total: Int,
    ) : Preview

    data class Database(
        val tables: List<Table>,
    ) : Preview

    /** A file shown as text, such as a Mermaid diagram that could not be drawn, with [note] saying why. */
    data class Source(
        val text: String,
        val note: String,
    ) : Preview
}

/** A notebook cell; [kind] is Jupyter's `cell_type`. */
data class Cell(
    val kind: String,
    val source: String,
    val outputs: List<Output> = emptyList(),
)

sealed interface Output {
    data class Text(
        val text: String,
        val error: Boolean = false,
    ) : Output

    class Image(
        val bitmap: ImageBitmap,
    ) : Output
}

/** An archive entry; [size] is -1 when the archive does not say. */
data class Entry(
    val name: String,
    val size: Long,
)

/** A table's first rows; [columns] is empty when it has none to show. */
data class Table(
    val name: String,
    val columns: List<String>,
    val rows: List<List<String>>,
)
