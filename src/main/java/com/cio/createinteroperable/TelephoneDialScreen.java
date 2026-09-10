package com.cio.createinteroperable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opened by right-clicking the Telephone's dial_ring. Two EditBoxes — Area
 * Code (digits only, up to 3) and the 6-character Number (digits, #, *) —
 * matching the same Area Code + Number split ownNumber() itself uses, sent
 * to the server as a single formatted {@link TelephoneDialPacket} when the
 * screen closes. Unlike ownNumber()'s own zero-padding, a short/incomplete
 * number here is sent as-is (not padded to 6) — dialing should require the
 * full real number, the same way an incomplete dial in real life just fails
 * to connect rather than silently completing itself.
 */
public class TelephoneDialScreen extends Screen {
    private final BlockPos pos;
    private final String initialAreaCode;
    private final String initialNumber;
    private EditBox areaCodeBox;
    private EditBox numberBox;

    public TelephoneDialScreen(BlockPos pos, String initialTarget) {
        super(Component.literal("Dial a Number"));
        this.pos = pos;
        String[] parts = initialTarget.split("-", 2);
        this.initialAreaCode = parts.length > 0 ? parts[0] : "";
        this.initialNumber = parts.length > 1 ? parts[1] : "";
    }

    @Override
    protected void init() {
        areaCodeBox = new EditBox(font, width / 2 - 80, height / 2 - 10, 60, 20, Component.literal("Area Code"));
        areaCodeBox.setMaxLength(3);
        areaCodeBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        areaCodeBox.setValue(initialAreaCode);
        addRenderableWidget(areaCodeBox);

        numberBox = new EditBox(font, width / 2 - 10, height / 2 - 10, 90, 20, Component.literal("Number"));
        numberBox.setMaxLength(6);
        numberBox.setFilter(s -> s.chars().allMatch(c -> Character.isDigit(c) || c == '#' || c == '*'));
        numberBox.setValue(initialNumber);
        addRenderableWidget(numberBox);

        setInitialFocus(areaCodeBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 42, 0xFFFFFF);
        graphics.drawCenteredString(font, "Area Code", width / 2 - 50, height / 2 - 22, 0xA0A0A0);
        graphics.drawCenteredString(font, "Number", width / 2 + 35, height / 2 - 22, 0xA0A0A0);
    }

    @Override
    public void removed() {
        int areaCode = areaCodeBox.getValue().isEmpty() ? 0 : Integer.parseInt(areaCodeBox.getValue());
        String target = TelephoneNumbers.formatNumber(areaCode, numberBox.getValue());
        PacketDistributor.sendToServer(new TelephoneDialPacket(pos, target));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
