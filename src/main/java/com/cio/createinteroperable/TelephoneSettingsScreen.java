package com.cio.createinteroperable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one telephone screen, for every phone type (CIO's Interoperable, CPG and
 * CEE telephones, and Iden's Decor's). Two modes:
 * <ul>
 *   <li><b>Settings</b> (CIO: right-click the back plate; Iden: sneak +
 *       right-click): this phone's own Area Code + Number and Label, the number
 *       it calls, Auto-Answer, and (CIO's own three telephones only) Pulse (3s) &mdash; sent as a {@link TelephoneSettingsPacket}.</li>
 *   <li><b>Dial</b> (CIO: right-click the front): only the number to call
 *       &mdash; sent as a {@link TelephoneDialPacket}.</li>
 * </ul>
 * Field conventions as before: area code up to 3 digits; number up to 6 of
 * digits, {@code #} and {@code *}. Saved when the screen closes (Done or Esc).
 */
public class TelephoneSettingsScreen extends Screen {
    private static final int PANEL_BG = 0xF0141414;
    private static final int PANEL_BORDER = 0xFF303030;
    private static final int LABEL_COLOR = 0xA0A0A0;
    private static final int HINT_COLOR = 0x707070;
    private static final int PANEL_W = 220;

    private final BlockPos pos;
    private final boolean dialOnly;
    private final String initialAreaCode;
    private final String initialNumber;
    private final String initialLabel;
    private final String initialDialArea;
    private final String initialDialNumber;
    private final boolean initialAutoAnswer;
    private final boolean pulseSupported;
    private final boolean initialPulse;

    private EditBox areaCodeBox;
    private EditBox numberBox;
    private EditBox labelBox;
    private EditBox dialAreaBox;
    private EditBox dialNumberBox;
    private Checkbox autoAnswerBox;
    private Checkbox pulseBox;

    public static TelephoneSettingsScreen settings(BlockPos pos, int areaCode, String number, String label,
                                                   String dialTarget, boolean autoAnswer, boolean pulseSupported,
                                                   boolean pulse) {
        return new TelephoneSettingsScreen(pos, false, areaCode, number, label, dialTarget, autoAnswer, pulseSupported, pulse);
    }

    public static TelephoneSettingsScreen dial(BlockPos pos, String dialTarget) {
        return new TelephoneSettingsScreen(pos, true, 0, "", "", dialTarget, false, false, false);
    }

    private TelephoneSettingsScreen(BlockPos pos, boolean dialOnly, int areaCode, String number, String label,
                                    String dialTarget, boolean autoAnswer, boolean pulseSupported, boolean pulse) {
        super(Component.literal(dialOnly ? "Dial" : "Telephone Settings"));
        this.pos = pos;
        this.dialOnly = dialOnly;
        this.initialAreaCode = String.format("%03d", areaCode);
        this.initialNumber = number;
        this.initialLabel = label;
        int dash = dialTarget.indexOf('-');
        this.initialDialArea = dash > 0 ? dialTarget.substring(0, dash) : "";
        this.initialDialNumber = dash > 0 ? dialTarget.substring(dash + 1) : "";
        this.initialAutoAnswer = autoAnswer;
        this.pulseSupported = pulseSupported;
        this.initialPulse = pulse;
    }

    private int panelHeight() {
        return dialOnly ? 88 : 176;
    }

    private int left() {
        return width / 2 - PANEL_W / 2;
    }

    private int top() {
        return height / 2 - panelHeight() / 2;
    }

    /** Y of the "Calls" row within the panel. */
    private int callsRow() {
        return dialOnly ? 32 : 82;
    }

    @Override
    protected void init() {
        int x = left() + 10;
        int y = top();

        if (!dialOnly) {
            areaCodeBox = areaBox(x + 80, y + 22, initialAreaCode, "Area Code");
            numberBox = numberBox(x + 124, y + 22, initialNumber, "Number");

            labelBox = new EditBox(font, x + 80, y + 48, 120, 18, Component.literal("Label"));
            labelBox.setMaxLength(32);
            labelBox.setValue(initialLabel);
            addRenderableWidget(labelBox);

            autoAnswerBox = Checkbox.builder(Component.literal("Auto-Answer"), font)
                    .pos(x, y + 112).selected(initialAutoAnswer).build();
            addRenderableWidget(autoAnswerBox);

            if (pulseSupported) {
                pulseBox = Checkbox.builder(Component.literal("Pulse (3s)"), font)
                        .pos(x + 110, y + 112).selected(initialPulse).build();
                pulseBox.setTooltip(Tooltip.create(Component.literal(
                        "Calls placed from this phone make the answering phone's Call Breaker, Call Feeds and redstone output switch on and off every 3 seconds.")));
                addRenderableWidget(pulseBox);
            }
        }

        dialAreaBox = areaBox(x + 80, y + callsRow(), initialDialArea, "Call Area Code");
        dialNumberBox = numberBox(x + 124, y + callsRow(), initialDialNumber, "Call Number");

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 40, y + panelHeight() - 28, 80, 20).build());

        setInitialFocus(dialOnly ? dialAreaBox : areaCodeBox);
    }

    private EditBox areaBox(int x, int y, String value, String name) {
        EditBox box = new EditBox(font, x, y, 36, 18, Component.literal(name));
        box.setMaxLength(3);
        box.setFilter(s -> s.chars().allMatch(Character::isDigit));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private EditBox numberBox(int x, int y, String value, String name) {
        EditBox box = new EditBox(font, x, y, 76, 18, Component.literal(name));
        box.setMaxLength(6);
        box.setFilter(s -> s.chars().allMatch(c -> Character.isDigit(c) || c == '#' || c == '*'));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        graphics.fill(x - 1, y - 1, x + PANEL_W + 1, y + panelHeight() + 1, PANEL_BORDER);
        graphics.fill(x, y, x + PANEL_W, y + panelHeight(), PANEL_BG);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, y + 6, 0xFFFFFF);
        if (!dialOnly) {
            graphics.drawString(font, "This phone", x + 10, y + 27, LABEL_COLOR);
            graphics.drawString(font, "Label", x + 10, y + 53, LABEL_COLOR);
        }
        graphics.drawString(font, "Calls", x + 10, y + callsRow() + 5, LABEL_COLOR);
        graphics.drawString(font, "Area", x + 90, y + callsRow() - 12, HINT_COLOR);
        graphics.drawString(font, "Number", x + 134, y + callsRow() - 12, HINT_COLOR);
    }

    @Override
    public void removed() {
        String dialNumber = dialNumberBox.getValue();
        String dialTarget = dialNumber.isEmpty() ? ""
                : TelephoneNumbers.formatNumber(parseArea(dialAreaBox.getValue()), dialNumber);
        if (dialOnly) {
            PacketDistributor.sendToServer(new TelephoneDialPacket(pos, dialTarget));
            return;
        }
        PacketDistributor.sendToServer(new TelephoneSettingsPacket(pos, parseArea(areaCodeBox.getValue()),
                numberBox.getValue(), labelBox.getValue(), dialTarget, autoAnswerBox.selected(),
                pulseBox != null && pulseBox.selected()));
    }

    private static int parseArea(String value) {
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
