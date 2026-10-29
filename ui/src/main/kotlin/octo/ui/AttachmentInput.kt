package octo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import octo.core.task.Attachment
import octo.core.task.NewAttachment
import octo.core.task.read
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JFileChooser

/** Where attached files come from and where reading one fails. */
internal class AttachmentInput(
    val onFiles: (List<NewAttachment>) -> Unit,
    val onError: (String) -> Unit,
) {
    /** Pasted files or a pasted picture; false when the clipboard holds neither, so text pastes as usual. */
    fun paste(): Boolean {
        val found =
            read {
                Toolkit
                    .getDefaultToolkit()
                    .systemClipboard
                    .getContents(null)
                    ?.let(::fromTransfer)
            }
        found?.let(onFiles)
        return found != null
    }

    fun drop(transferable: Transferable): Boolean {
        val found = read { fromTransfer(transferable) }
        found?.let(onFiles)
        return found != null
    }

    fun choose() {
        val chooser = JFileChooser(System.getProperty("user.home")).apply { isMultiSelectionEnabled = true }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        read { fromFiles(chooser.selectedFiles.toList()) }?.let(onFiles)
    }

    // The clipboard and the files belong to other programs, so any of them may be gone or unreadable.
    private fun read(block: () -> List<NewAttachment>?): List<NewAttachment>? =
        try {
            block()?.takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            onError("Could not attach: ${e.message}")
            null
        } catch (e: UnsupportedFlavorException) {
            onError("Could not attach: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            onError(e.message ?: "Could not attach")
            null
        } catch (e: IllegalStateException) {
            onError("Could not attach: ${e.message}")
            null
        }

    companion object {
        private const val PASTED_NAME = "pasted-image.png"

        private fun fromTransfer(transferable: Transferable): List<NewAttachment>? =
            when {
                transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                    fromFiles(
                        (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                            .orEmpty()
                            .filterIsInstance<File>(),
                    )
                }

                transferable.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
                    (transferable.getTransferData(DataFlavor.imageFlavor) as? Image)?.let { listOf(png(it)) }
                }

                else -> {
                    null
                }
            }

        fun fromFiles(files: List<File>): List<NewAttachment> = files.filter { it.isFile }.map(NewAttachment::read)

        private fun png(image: Image): NewAttachment {
            val width = image.getWidth(null)
            val height = image.getHeight(null)
            check(width > 0 && height > 0) { "the pasted picture is not ready" }
            val buffered =
                image as? BufferedImage ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
                    val g = it.createGraphics()
                    g.drawImage(image, 0, 0, null)
                    g.dispose()
                }
            val out = ByteArrayOutputStream()
            ImageIO.write(buffered, "png", out)
            return NewAttachment(PASTED_NAME, "image/png", out.toByteArray())
        }
    }
}

/** Takes a paste of files or a picture (Ctrl/Cmd+V) and files dropped on the field. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.takesAttachments(input: AttachmentInput): Modifier {
    val target =
        remember(input) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent) = input.drop(event.awtTransferable)
            }
        }
    return onPreviewKeyEvent {
        val paste = it.type == KeyEventType.KeyDown && it.key == Key.V && (it.isMetaPressed || it.isCtrlPressed)
        paste && input.paste()
    }.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}

@Composable
internal fun AttachButton(input: AttachmentInput) {
    GhostButton(onClick = input::choose) { Text("Attach") }
}

/** The files waiting to go out; a click takes one off. */
@Composable
internal fun PendingAttachments(
    files: List<NewAttachment>,
    onRemove: (Int) -> Unit,
) {
    if (files.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
        files.forEachIndexed { index, file ->
            InputChip(
                selected = false,
                onClick = { onRemove(index) },
                label = { Text(file.name) },
                trailingIcon = { Text("×") },
            )
        }
    }
}

/** How a sent message lists its files. */
internal fun List<Attachment>.label(): String = joinToString(", ", prefix = "Attached: ") { it.name }

@Composable
internal fun AttachedFiles(files: List<Attachment>) {
    if (files.isNotEmpty()) Text(files.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
