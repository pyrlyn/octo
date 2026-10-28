package octo.ui.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/** Reads a Jupyter notebook (nbformat 4) into cells and their outputs. */
internal object Notebooks {
    private const val MAX_CELLS = 500

    fun parse(text: String): Preview.Notebook {
        val cells = Json.parseToJsonElement(text).jsonObject["cells"] ?: error("Not a Jupyter notebook")
        return Preview.Notebook(cells.jsonArray.take(MAX_CELLS).map { cell(it.jsonObject) })
    }

    private fun cell(cell: JsonObject) =
        Cell(
            kind = cell.string("cell_type") ?: "raw",
            source = joined(cell["source"]),
            outputs = (cell["outputs"] as? JsonArray).orEmpty().mapNotNull { output(it.jsonObject) },
        )

    private fun output(output: JsonObject): Output? =
        when (output.string("output_type")) {
            "stream" -> Output.Text(joined(output["text"]), error = output.string("name") == "stderr")
            "error" -> Output.Text("${output.string("ename")}: ${output.string("evalue")}", error = true)
            "execute_result", "display_data" -> data(output["data"] as? JsonObject ?: JsonObject(emptyMap()))
            else -> null
        }

    private fun data(data: JsonObject): Output? {
        val text = data["text/plain"]?.let { Output.Text(joined(it)) }
        val png = data["image/png"] ?: return text
        // One broken picture should not hide the rest of the notebook.
        return runCatching { Output.Image(Pictures.raster(Base64.getMimeDecoder().decode(joined(png)))) }
            .getOrElse { text ?: Output.Text("[image could not be shown]", error = true) }
    }

    // Jupyter stores multi-line text either as one string or as a list of lines.
    private fun joined(value: JsonElement?): String =
        when (value) {
            is JsonArray -> value.joinToString("") { it.jsonPrimitive.content }
            is JsonPrimitive -> value.content
            else -> ""
        }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.content
}
