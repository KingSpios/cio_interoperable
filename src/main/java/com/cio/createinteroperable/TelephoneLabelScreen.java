package com.cio.createinteroperable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opened by right-clicking the Telephone's top face — free-text name for this phone. */
public class TelephoneLabelScreen extends Screen {
    private final BlockPos pos;
    private final String initialValue;
    private EditBox labelBox;

    public TelephoneLabelScreen(BlockPos pos, String initialValue) {
        super(Component.literal("Label this Telephone"));
        this.pos = pos;
        this.initialValue = initialValue;
    }

    @Override
    protected void init() {
        labelBox = new EditBox(font, width / 2 - 75, height / 2 - 10, 150, 20, Component.literal("Label"));
        labelBox.setMaxLength(100);
        labelBox.setValue(initialValue);
        addRenderableWidget(labelBox);
        setInitialFocus(labelBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(new TelephoneLabelPacket(pos, labelBox.getValue()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
