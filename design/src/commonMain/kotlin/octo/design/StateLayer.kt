package octo.design

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * Pyrlyn's pointer feedback: a flat tint on hover and a deeper one on press, as in the design,
 * instead of Material's ripple. Every `clickable` without its own indication gets it.
 */
internal class StateLayer(
    private val hover: Color,
    private val press: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        StateLayerNode(interactionSource, hover, press)

    override fun equals(other: Any?): Boolean = other is StateLayer && other.hover == hover && other.press == press

    override fun hashCode(): Int = 31 * hover.hashCode() + press.hashCode()
}

private class StateLayerNode(
    private val source: InteractionSource,
    private val hover: Color,
    private val press: Color,
) : Modifier.Node(),
    DrawModifierNode {
    private var hovers = 0
    private var presses = 0
    private var focuses = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect {
                when (it) {
                    is HoverInteraction.Enter -> hovers++
                    is HoverInteraction.Exit -> hovers--
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                    is FocusInteraction.Focus -> focuses++
                    is FocusInteraction.Unfocus -> focuses--
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        when {
            presses > 0 -> drawRect(press)
            hovers > 0 || focuses > 0 -> drawRect(hover)
        }
    }
}
