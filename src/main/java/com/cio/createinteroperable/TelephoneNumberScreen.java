package com.cio.createinteroperable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opened by right-clicking anywhere on the Telephone's back_plate. Same
 * Area Code + Number field layout as TelephoneDialScreen, but for SETTING
 * this phone's own identity rather than dialing out — Area Code entered
 * here still writes through to the same in-world slider value
 * (TelephoneBlockEntity#setOwnNumber calls areaCodeValue.setValue(...)), it
 * isn't a separate setting. A darker grey panel behind the fields (vs. the
 * dial screen's plain background) is the only visual difference, so the two
 * screens are distinguishable at a glance despite the identical layout.
 */
public class TelephoneNumberScreen extends Screen {
    private static final int PANEL_BG = 0xF0141414;
    private static final int PANEL_BORDER = 0xFF303030;

    private final BlockPos pos;
    private final String initialAreaCode;
    private final String initialNumber;
    private EditBox areaCodeBox;
    private EditBox numberBox;

    public TelephoneNumberScreen(BlockPos pos, int initialAreaCode, String initialNumber) {
        super(Component.literal("Set Telephone Number"));
        this.pos = pos;
        this.initialAreaCode = String.format("%03d", initialAreaCode);
        this.initialNumber = initialNumber;
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

        int panelX = width / 2 - 90, panelY = height / 2 - 32;
        int panelW = 180, panelH = 60;
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 42, 0xFFFFFF);
        graphics.drawCenteredString(font, "Area Code", width / 2 - 50, height / 2 - 22, 0xA0A0A0);
        graphics.drawCenteredString(font, "Number", width / 2 + 35, height / 2 - 22, 0xA0A0A0);
    }

    @Override
    public void removed() {
        int areaCode = areaCodeBox.getValue().isEmpty() ? 0 : Integer.parseInt(areaCodeBox.getValue());
        PacketDistributor.sendToServer(new TelephoneNumberPacket(pos, areaCode, numberBox.getValue()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
