package octo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import octo.core.attachments.Attachment
import octo.core.attachments.Attachments
import octo.core.attachments.FileUpload
import octo.ui.vm.FilePicker
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

/** Where the user's files come from on the desktop: a file chooser, a drop or the clipboard. */
internal object DesktopFiles {
    fun choose(): List<File> {
        val chooser = JFileChooser(System.getProperty("user.home")).apply { isMultiSelectionEnabled = true }
        return if (chooser.showOpenDialog(null) ==
            JFileChooser.APPROVE_OPTION
        ) {
            chooser.selectedFiles.toList()
        } else {
            emptyList()
        }
    }

    /** Throws [IllegalArgumentException] for a file too large to attach, before reading any of it. */
    fun read(files: List<File>): List<FileUpload> =
        files.map { file ->
            require(file.isFile) { "${file.name} is not a file" }
            require(file.length() <= Attachments.MAX_FILE_BYTES) {
                "${file.name} is larger than ${Attachments.MAX_FILE_BYTES shr MIB_SHIFT} MB"
            }
            FileUpload(file.name, file.readBytes())
        }

    /**
     * Files or an image in [transferable]; null when it holds neither, so plain text goes on to
     * the text field. Copied text that also offers a picture of itself stays text.
     */
    fun from(transferable: Transferable): List<FileUpload>? =
        when {
            transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                read((transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>())
            }

            transferable.isDataFlavorSupported(DataFlavor.imageFlavor) &&
                !transferable.isDataFlavorSupported(DataFlavor.stringFlavor) -> {
                listOf(FileUpload(PASTED_NAME, png(transferable.getTransferData(DataFlavor.imageFlavor) as Image)))
            }

            else -> {
                null
            }
        }

    fun clipboard(): List<FileUpload>? =
        from(Toolkit.getDefaultToolkit().systemClipboard.getContents(null) ?: return null)

    private fun png(image: Image): ByteArray {
        val buffered =
            image as? BufferedImage
                ?: BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB).also {
                    it.createGraphics().apply {
                        drawImage(image, 0, 0, null)
                        dispose()
                    }
                }
        return ByteArrayOutputStream().also { ImageIO.write(buffered, "png", it) }.toByteArray()
    }

    private const val PASTED_NAME = "pasted-image.png"
    private const val MIB_SHIFT = 20
}

/**
 * Runs [load] and hands its files to [picker]; whatever the desktop throws while reading them is
 * a reason for the picker to show, never a crash of the window.
 */
private fun deliver(
    picker: FilePicker,
    load: () -> List<FileUpload>?,
): Boolean =
    try {
        load()?.also { if (it.isNotEmpty()) picker.add(it) } != null
    } catch (e: IllegalArgumentException) {
        picker.reject(e.message ?: "That file cannot be attached")
        true
    } catch (e: IOException) {
        picker.reject("Could not read the file: ${e.message}")
        true
    } catch (e: UnsupportedFlavorException) {
        picker.reject("Could not read what was dropped or pasted: ${e.message}")
        true
    }

@Composable
internal fun AttachButton(picker: FilePicker) {
    TextButton(onClick = { deliver(picker) { DesktopFiles.read(DesktopFiles.choose()) } }) {
        Text("Attach")
    }
}

/** Cmd/Ctrl+V with files or an image on the clipboard attaches them; true when it did. */
internal fun pasteFiles(
    event: KeyEvent,
    picker: FilePicker,
): Boolean {
    val paste = event.type == KeyEventType.KeyDown && event.key == Key.V && (event.isMetaPressed || event.isCtrlPressed)
    return paste && deliver(picker, DesktopFiles::clipboard)
}

/** Files dropped anywhere on the modified element are attached. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.dropFiles(picker: FilePicker): Modifier {
    val target =
        remember(picker) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean =
                    deliver(picker) { DesktopFiles.from(event.awtTransferable) }
            }
        }
    return dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}

/** Files picked for a message that is not sent yet; each can be taken off again. */
@Composable
internal fun PickedFiles(
    files: List<FileUpload>,
    picker: FilePicker,
) {
    if (files.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
        files.forEachIndexed { i, file ->
            InputChip(
                selected = false,
                onClick = { picker.remove(i) },
                label = { Text(file.name) },
                trailingIcon = { Text("×") },
            )
        }
    }
}

/** The files of a sent message; a revert deletes them and leaves their names. */
@Composable
internal fun AttachedFiles(attachments: List<Attachment>) {
    if (attachments.isEmpty()) return
    Text(
        "Attached: " + attachments.joinToString(", ") { if (it.removed) "${it.name} (removed)" else it.name },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
