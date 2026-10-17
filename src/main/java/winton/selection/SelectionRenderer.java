/*
 * This file is part of Winton.
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Winton is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Winton.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Based on Baritone (https://github.com/cabaletta/baritone).
 * Copyright (c) Baritone contributors. Modifications Copyright (c) WinTone contributors.
 */

package winton.selection;

import winton.Winton;
import winton.api.event.events.RenderEvent;
import winton.api.event.listener.AbstractGameEventListener;
import winton.api.selection.ISelection;
import winton.utils.IRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;

public class SelectionRenderer implements IRenderer, AbstractGameEventListener {

    public static final double SELECTION_BOX_EXPANSION = .005D;

    private final SelectionManager manager;

    SelectionRenderer(Winton winton, SelectionManager manager) {
        this.manager = manager;
        winton.getGameEventHandler().registerEventListener(this);
    }

    public static void renderSelections(PoseStack stack, ISelection[] selections) {
        float opacity = settings.selectionOpacity.value;
        boolean ignoreDepth = settings.renderSelectionIgnoreDepth.value;
        float lineWidth = settings.selectionLineWidth.value;

        if (!settings.renderSelection.value || selections.length == 0) {
            return;
        }

        BufferBuilder bufferBuilder = IRenderer.startLines(settings.colorSelection.value, opacity);

        for (ISelection selection : selections) {
            IRenderer.emitAABB(bufferBuilder, stack, selection.aabb(), SELECTION_BOX_EXPANSION, lineWidth);
        }

        if (settings.renderSelectionCorners.value) {
            IRenderer.glColor(settings.colorSelectionPos1.value, opacity);

            for (ISelection selection : selections) {
                IRenderer.emitAABB(bufferBuilder, stack, new AABB(selection.pos1()), lineWidth);
            }

            IRenderer.glColor(settings.colorSelectionPos2.value, opacity);

            for (ISelection selection : selections) {
                IRenderer.emitAABB(bufferBuilder, stack, new AABB(selection.pos2()), lineWidth);
            }
        }

        IRenderer.endLines(bufferBuilder, ignoreDepth);
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        if (!settings.renderSelection.value) {
            return;
        }
        ISelection[] selections = manager.getSelections();
        if (selections.length == 0) {
            return;
        }
        renderSelections(event.getModelViewStack(), selections);
    }
}
