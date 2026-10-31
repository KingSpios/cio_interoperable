package com.cio.createinteroperable.telephone;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Radio Telephone's screen (ordinary phones have {@link TelephoneScreen}). Two modes:
 * <ul>
 *   <li><b>Settings</b> (CIO: right-click the back plate; Iden: sneak +
 *       right-click): this phone's own Area Code + Number and Label, the number
 *       it calls, Auto-Answer, and (CIO's own three telephones only) Pulse (3s) &mdash; sent as a {@link TelephoneSettingsPacket}.
 *       A radio telephone adds its band on a faceplate to the right: a {@link RadioDialWidget} and a typed start
 *       channel, the band's width, and its transmit level &mdash; sent as a {@link RadioTuningPacket}.</li>
 *   <li><b>Dial</b> (CIO: right-click the front): only the number to call
 *       &mdash; sent as a {@link TelephoneDialPacket}. The phone's dial tone plays
 *       while it is open.</li>
 * </ul>
 * A radio telephone's settings end in an RTI strip across the panel: its route (Direct / RTI /
 * Off lamps, {@link RadioRouting}), a key-switch locking it to the interconnect ID in the window
 * beside it, and a PING RTI key whose answer (and, on the RTI route, the interconnect in use)
 * shows on the LCD beside it, Done at the row's end &mdash; sent as a {@link RadioRoutePacket}.
 * Drawn as the black telephone in chunky pixels (art pixels 2x2, {@code textures/gui/telephone.png},
 * generated from {@code telephone_black.png}'s palette): the panel is put together from the sheet's
 * strips, so each layout is its own size. Fields sit in recessed number windows, the toggles are the
 * phone's green lamp, Done is a paper key.
 * Field conventions as before: area code up to 3 digits; number up to 6 of
 * digits, {@code #} and {@code *}. Saved when the screen closes (Done or Esc).
 */
public class TelephoneSettingsScreen extends Screen {
    private static final ResourceLocation TEXTURE = CreateInteroperable.rl("textures/gui/telephone.png");
    private static final int TEX = 512;
    /** Art pixels are 2x2; positions below are art pixels. */
    private static final int S = 2;
    private static final int BODY_W = 196, BODY_H = 112, CORNER = 6;
    private static final int PLAIN_W = 110, RADIO_W = 208;
    /** Left column: captions at {@link #CAPTION_X}, windows from {@link #FIELD_X}; the radio faceplate from {@link #RADIO_X}. */
    private static final int CAPTION_X = 7, FIELD_X = 46, AREA_W = 18, NUMBER_X = 68, COLUMN_END = 105;
    private static final int RADIO_X = 113, RADIO_FIELD_X = 152, RADIO_END = 203;
    /** The RTI strip: the route lamps' x, the key-switch and its ID window, the LCD, the key-switch sprites. */
    /** Each route lamp has a fixed slot (a long translation shrinks into it), the three ending clear of the key-switch. */
    private static final int ROUTE_X = 34, ROUTE_SLOT = 25, RTI_KEY_X = RADIO_X, RTI_ID_X0 = 150, RTI_ID_X1 = 174;
    /** The ping/LCD/Done row keeps this far from the panel's sides: clear of the bottom screws (x 5-8). */
    private static final int RTI_EDGE = 10;
    private static final int RTI_LCD_X0 = RTI_EDGE + 40 + 4, RTI_KEY = 10, RTI_KEY_U = 400, RTI_KEY_V = 200, RTI_KEY_LOCKED_U = 424;
    /** Space between the last content and the keys, and below the keys to the panel's edge. */
    private static final int KEY_GAP_ABOVE = 6, KEY_GAP_BELOW = 6;
    private static final int WINDOW_H = 9, LAMP_ROW = 11, KEY_W = 40, KEY_H = 11, KEY_GAP = 6;
    /** The Dial key on the sheet (texture px): OFF, OFF hover, ON (line in use), ON hover; its lamp ends at art x 8. */
    private static final int DIAL_U = 400, DIAL_OFF_V = 100, DIAL_OFF_HOVER_V = 124, DIAL_ON_V = 148, DIAL_ON_HOVER_V = 172;
    private static final int DIAL_LAMP_END = 8;

    private static final int TITLE = 0xFFD9D9DA;
    private static final int LABEL = 0xFFC5C3BC;
    private static final int HINT = 0xFFACA89F;
    private static final int FIELD_TEXT = 0xFFC5C3BC;
    private static final int KEY_TEXT = 0xFF2B2B31;
    private static final int READOUT = 0xFF43E88D;
    private static final int BRASS = 0xFFCEAD32;
    private static final int GROOVE_DARK = 0xFF313037, GROOVE_LIGHT = 0xFF464649;

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
    @Nullable
    private final RadioTuning initialTuning;
    private final boolean initialKeepLoaded;

    private EditBox areaCodeBox;
    private EditBox numberBox;
    private EditBox labelBox;
    private EditBox dialAreaBox;
    private EditBox dialNumberBox;
    private Lamp autoAnswerLamp;
    private Lamp pulseLamp;
    private Lamp keepLoadedLamp;
    /** A key was typed into the number to call: the dial tone has stopped. */
    private boolean keyed;
    /** A radio telephone's dial screen (the settings screen knows this from its tuning). */
    private boolean radioPhone;
    private RadioDialWidget radioDial;
    private EditBox channelBox;
    private EditBox levelBox;
    private EditBox widthBox;
    /** The RTI strip (radio settings): route, lock, its ID window; a ping waiting for its answer. */
    private int route = RadioRouting.DIRECT;
    private int rtiLock = RadioRouting.NO_LOCK;
    private EditBox rtiIdBox;
    private boolean pingPending;
    /** Every number window, in art pixels, with the box typed into it (for the focus rim). */
    private final List<Window> windows = new ArrayList<>();

    private record Window(EditBox box, int x0, int y0, int x1) {
    }

    public static TelephoneSettingsScreen settings(BlockPos pos, int areaCode, String number, String label,
                                                   String dialTarget, boolean autoAnswer, boolean pulseSupported,
                                                   boolean pulse, @Nullable RadioTuning tuning, boolean keepLoaded) {
        return new TelephoneSettingsScreen(pos, false, areaCode, number, label, dialTarget, autoAnswer, pulseSupported, pulse,
                tuning, keepLoaded);
    }

    /** The dial-out screen; {@code radio} marks a radio telephone (whose keys have no desk bell). */
    public static TelephoneSettingsScreen dial(BlockPos pos, String dialTarget, boolean radio) {
        TelephoneSettingsScreen screen = new TelephoneSettingsScreen(pos, true, 0, "", "", dialTarget, false, false, false, null, false);
        screen.radioPhone = radio;
        return screen;
    }

    private TelephoneSettingsScreen(BlockPos pos, boolean dialOnly, int areaCode, String number, String label,
                                    String dialTarget, boolean autoAnswer, boolean pulseSupported, boolean pulse,
                                    @Nullable RadioTuning tuning, boolean keepLoaded) {
        super(Lang.component(dialOnly ? "telephone.dial" : "telephone.telephone_settings"));
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
        this.initialTuning = tuning;
        this.initialKeepLoaded = keepLoaded;
    }

    // --- layout (art pixels) ------------------------------------------------------------

    private boolean radio() {
        return initialTuning != null && !dialOnly;
    }

    private int panelW() {
        return radio() ? RADIO_W : PLAIN_W;
    }

    /** Top of the "Calls" windows. */
    private int callsRow() {
        return dialOnly ? 24 : 48;
    }

    /** Top of the first lamp row (settings only). */
    private static final int LAMPS_ROW = 61;
    /** The radio faceplate: dial, the three windows (caption above each), the readout, the chunk lamp. */
    private static final int DIAL_Y = 25, BAND_ROW = 23, WIDTH_ROW = 39, LEVEL_ROW = 55, READOUT_ROW = 68, KEEP_ROW = 81;

    /** Where the last row of content ends; the keys sit {@link #KEY_GAP_ABOVE} below it. */
    private int contentEnd() {
        if (dialOnly) {
            return callsRow() + WINDOW_H;
        }
        int left = LAMPS_ROW + (pulseSupported ? 2 : 1) * LAMP_ROW - 3;
        return radio() ? Math.max(left, KEEP_ROW + 8) : left;
    }

    private int keyY() {
        return radio() ? rtiRow2() : contentEnd() + KEY_GAP_ABOVE;
    }

    /** The RTI strip (radio settings): a groove under the content, the route/lock row, the ping/LCD/Done row. */
    private int rtiSeparator() {
        return contentEnd() + 2;
    }

    private int rtiRow1() {
        return rtiSeparator() + 4;
    }

    private int rtiRow2() {
        return rtiRow1() + 13;
    }

    /** Done: centred, or at the RTI strip's second row's end. */
    private int doneX() {
        return radio() ? panelW() - RTI_EDGE - KEY_W : (panelW() - KEY_W) / 2;
    }

    private int panelH() {
        return keyY() + KEY_H + KEY_GAP_BELOW;
    }

    private int left() {
        return (width - panelW() * S) / 2;
    }

    private int top() {
        return (height - panelH() * S) / 2;
    }

    private int sx(double artX) {
        return left() + (int) Math.round(artX * S);
    }

    private int sy(double artY) {
        return top() + (int) Math.round(artY * S);
    }

    @Override
    protected void init() {
        windows.clear();
        if (!dialOnly) {
            areaCodeBox = areaBox(FIELD_X, 18, initialAreaCode, "telephone.screen.area_code");
            numberBox = numberBox(NUMBER_X, 18, initialNumber, "telephone.screen.number");

            labelBox = window(FIELD_X, 30, COLUMN_END, Lang.component("telephone.label"));
            labelBox.setMaxLength(32);
            labelBox.setValue(initialLabel);
        }

        dialAreaBox = areaBox(FIELD_X, callsRow(), initialDialArea, "telephone.screen.call_area_code");
        dialNumberBox = numberBox(NUMBER_X, callsRow(), initialDialNumber, "telephone.screen.call_number");
        keyTones(dialAreaBox);
        keyTones(dialNumberBox);

        if (!dialOnly) {
            autoAnswerLamp = lamp(CAPTION_X, LAMPS_ROW, COLUMN_END, "telephone.auto_answer", initialAutoAnswer);
            if (pulseSupported) {
                pulseLamp = lamp(CAPTION_X, LAMPS_ROW + LAMP_ROW, COLUMN_END, "telephone.pulse_3s", initialPulse);
                pulseLamp.setTooltip(Tooltip.create(Lang.component("telephone.calls_placed_from_this_phone_make_th")));
            }
            if (radio()) {
                initRadio();
            }
        }

        if (dialOnly) {
            int keysX = (panelW() - 2 * KEY_W - KEY_GAP) / 2;
            addRenderableWidget(new DialKey(sx(keysX), sy(keyY())));
            addRenderableWidget(new Key(sx(keysX + KEY_W + KEY_GAP), sy(keyY()), Lang.component("telephone.done")));
        } else {
            addRenderableWidget(new Key(sx(doneX()), sy(keyY()), Lang.component("telephone.done")));
        }
        setInitialFocus(dialOnly ? dialAreaBox : areaCodeBox);
    }

    /** The frequency dial and the typed band start, kept in step; the band's width; the transmit level. */
    private void initRadio() {
        radioDial = new RadioDialWidget(sx(RADIO_X), sy(DIAL_Y), initialTuning.channel(), S, this::dialTurned);
        radioDial.setTooltip(Tooltip.create(Lang.component("telephone.screen.dial_tooltip")));
        addRenderableWidget(radioDial);

        channelBox = window(RADIO_FIELD_X, BAND_ROW, RADIO_FIELD_X + 22, Lang.component("telephone.screen.channel"));
        channelBox.setMaxLength(4);
        channelBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        channelBox.setValue(Integer.toString(initialTuning.channel()));
        channelBox.setResponder(text -> {
            if (!text.isEmpty()) {
                radioDial.setChannel(RadioTuning.clampChannel(Integer.parseInt(text)));
            }
        });

        widthBox = window(RADIO_FIELD_X, WIDTH_ROW, RADIO_FIELD_X + 14, Lang.component("telephone.screen.band_width"));
        widthBox.setMaxLength(2);
        widthBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        widthBox.setValue(Integer.toString(initialTuning.width()));

        levelBox = window(RADIO_FIELD_X, LEVEL_ROW, RADIO_FIELD_X + 14, Lang.component("telephone.screen.tx_level"));
        levelBox.setMaxLength(2);
        levelBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        levelBox.setValue(Integer.toString(initialTuning.level()));

        keepLoadedLamp = lamp(RADIO_X, KEEP_ROW, RADIO_END, "telephone.screen.keep_loaded", initialKeepLoaded);
        keepLoadedLamp.setTooltip(Tooltip.create(Lang.component("telephone.screen.keep_loaded_tooltip")));
        initRti();
    }

    /** The RTI strip: the three route lamps, the key-switch and its ID window, the PING RTI key. */
    private void initRti() {
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof RadioRouting phone) {
            route = phone.routeIndex();
            rtiLock = phone.rtiLock();
        }
        String[] names = {"direct", "rti", "off"};
        int x = ROUTE_X;
        for (int i = 0; i < names.length; i++) {
            RouteLamp lamp = new RouteLamp(sx(x), sy(rtiRow1()), i, Lang.component("telephone.screen.route." + names[i]));
            lamp.setTooltip(Tooltip.create(Lang.component("telephone.screen.route." + names[i] + "_tooltip")));
            addRenderableWidget(lamp);
            x += ROUTE_SLOT;
        }
        KeySwitch key = new KeySwitch(sx(RTI_KEY_X), sy(rtiRow1() - 1));
        key.setTooltip(Tooltip.create(Lang.component("telephone.screen.rti_lock_tooltip")));
        addRenderableWidget(key);
        rtiIdBox = window(RTI_ID_X0, rtiRow1(), RTI_ID_X1, Lang.component("telephone.screen.rti_id"));
        rtiIdBox.setMaxLength(4);
        rtiIdBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        rtiIdBox.setValue(rtiLock == RadioRouting.NO_LOCK ? "" : Integer.toString(rtiLock));
        rtiIdBox.setTooltip(Tooltip.create(Lang.component("telephone.screen.rti_id_tooltip")));
        ActionKey ping = new ActionKey(sx(RTI_EDGE), sy(rtiRow2()), Lang.component("telephone.screen.ping_rti"), () -> {
            pingPending = true;
            sendRoute(true);
        });
        ping.setTooltip(Tooltip.create(Lang.component("telephone.screen.ping_rti_tooltip")));
        addRenderableWidget(ping);
    }

    /** The typed interconnect ID, or {@link RadioRouting#NO_LOCK}. */
    private int typedRtiId() {
        String text = rtiIdBox.getValue();
        int id = text.isEmpty() ? RadioRouting.NO_LOCK : Integer.parseInt(text);
        return id < 1 || id > RadioRouting.MAX_LOCK ? RadioRouting.NO_LOCK : id;
    }

    private void sendRoute(boolean ping) {
        PacketDistributor.sendToServer(new RadioRoutePacket(pos, route, rtiLock, ping));
    }

    @Nullable
    private RadioRouting routing() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof RadioRouting phone
                ? phone : null;
    }

    /** The dial moved: the typed channel follows (without echoing back into the dial). */
    private void dialTurned(int channel) {
        String text = Integer.toString(channel);
        if (!text.equals(channelBox.getValue())) {
            channelBox.setValue(text);
        }
    }

    private int typedWidth() {
        String text = widthBox.getValue();
        return RadioTuning.clampWidth(radioDial.channel(), text.isEmpty() ? 1 : Integer.parseInt(text));
    }

    private int typedLevel() {
        String text = levelBox.getValue();
        return text.isEmpty() ? 0 : RadioTuning.clampLevel(Integer.parseInt(text));
    }

    @Override
    public void tick() {
        super.tick();
        // As on a real phone, the dial tone stops at the first key.
        if (dialOnly && !keyed) {
            TelephoneClient.keepDialTone(pos);
        }
        RadioRouting phone = rtiIdBox != null ? routing() : null;
        if (pingPending && phone != null && phone.pingedId() != RadioRouting.NO_LOCK) {
            pingPending = false;
            if (rtiIdBox.getValue().isEmpty()) {
                rtiIdBox.setValue(Integer.toString(phone.pingedId()));
            }
        }
    }

    /** Each key typed into {@code box} sounds its keypad tone at the phone. */
    private void keyTones(EditBox box) {
        String[] last = {box.getValue()};
        box.setResponder(text -> {
            String before = last[0];
            last[0] = text;
            if (text.length() != before.length() + 1) {
                return;
            }
            int i = 0;
            while (i < before.length() && text.charAt(i) == before.charAt(i)) {
                i++;
            }
            keyed = true;
            TelephoneClient.keyTone(pos, text.charAt(i), initialTuning == null && !radioPhone);
        });
    }

    /** A borderless box typed into the number window from art x {@code x0} to {@code x1}, top {@code y0}. */
    private EditBox window(int x0, int y0, int x1, Component name) {
        EditBox box = new EditBox(font, sx(x0) + 4, sy(y0) + 5, (x1 - x0) * S - 8, 10, name);
        box.setBordered(false);
        box.setTextColor(FIELD_TEXT);
        windows.add(new Window(box, x0, y0, x1));
        addRenderableWidget(box);
        return box;
    }

    private EditBox areaBox(int x, int y, String value, String name) {
        EditBox box = window(x, y, x + AREA_W, Lang.component(name));
        box.setMaxLength(3);
        box.setFilter(s -> s.chars().allMatch(Character::isDigit));
        box.setValue(value);
        return box;
    }

    private EditBox numberBox(int x, int y, String value, String name) {
        EditBox box = window(x, y, COLUMN_END, Lang.component(name));
        box.setMaxLength(6);
        box.setFilter(s -> s.chars().allMatch(c -> Character.isDigit(c) || c == '#' || c == '*'));
        box.setValue(value);
        return box;
    }

    private Lamp lamp(int x, int y, int end, String key, boolean lit) {
        Lamp lamp = new Lamp(sx(x), sy(y), (end - x) * S, Lang.component(key), lit);
        addRenderableWidget(lamp);
        return lamp;
    }

    // --- drawing --------------------------------------------------------------------

    /**
     * The panel, under the widgets: {@code Screen.render} calls this first. (Drawing it in
     * {@link #render} ahead of {@code super.render} put the menu background, and with it
     * the world blur, on top of it.)
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawBody(graphics);
        int w = panelW();
        strip(graphics, 0, 250, 190, 10, 10, 5, w - 10);
        for (int[] screw : new int[][]{{5, 9}, {w - 8, 9}, {5, panelH() - 8}, {w - 8, panelH() - 8}}) {
            graphics.blit(TEXTURE, sx(screw[0]), sy(screw[1]), 400, 90, 3 * S, 3 * S, TEX, TEX);
        }
        for (Window window : windows) {
            strip(graphics, 0, 228, 100, WINDOW_H, window.x0, window.y0, window.x1);
        }
        if (radio()) {
            // The faceplate's seam, from under the title to the end of the content.
            graphics.fill(sx(108), sy(18), sx(109), sy(contentEnd()), GROOVE_DARK);
            graphics.fill(sx(109), sy(18), sx(110), sy(contentEnd()), GROOVE_LIGHT);
            strip(graphics, 0, 274, 80, WINDOW_H, RADIO_X, READOUT_ROW, RADIO_END);
            // The RTI strip: a groove across the panel, the LCD between PING RTI and Done.
            int groove = rtiSeparator();
            graphics.fill(sx(CAPTION_X), sy(groove), sx(panelW() - CAPTION_X), sy(groove) + 1, GROOVE_DARK);
            graphics.fill(sx(CAPTION_X), sy(groove) + 1, sx(panelW() - CAPTION_X), sy(groove) + 2, GROOVE_LIGHT);
            strip(graphics, 0, 274, 80, WINDOW_H, RTI_LCD_X0, rtiRow2() + 1, rtiLcdEnd());
        }
    }

    private int rtiLcdEnd() {
        return doneX() - 4;
    }

    /** The body at the panel's size: the sheet's body less its far edges, then those edges. */
    private void drawBody(GuiGraphics graphics) {
        int w = panelW() - CORNER, h = panelH() - CORNER, c = CORNER * S;
        int x = left(), y = top();
        // Taller than the sprite: more of its middle rows (past its top edge), never its bottom edge.
        int first = Math.min(h, BODY_H - CORNER), middle = BODY_H - 2 * CORNER;
        bodyRows(graphics, x, y, 0, first, w);
        graphics.blit(TEXTURE, x + w * S, y, (BODY_W - CORNER) * S, 0, c, first * S, TEX, TEX);
        for (int done = first; done < h; done += middle) {
            int piece = Math.min(middle, h - done);
            bodyRows(graphics, x, y + done * S, CORNER, piece, w);
            graphics.blit(TEXTURE, x + w * S, y + done * S, (BODY_W - CORNER) * S, CORNER * S, c, piece * S, TEX, TEX);
        }
        bodyRows(graphics, x, y + h * S, BODY_H - CORNER, CORNER, w);
        graphics.blit(TEXTURE, x + w * S, y + h * S, (BODY_W - CORNER) * S, (BODY_H - CORNER) * S, c, c, TEX, TEX);
    }

    /**
     * {@code rows} art rows of the body sprite from its row {@code v}, {@code w} art wide: its own
     * left part, then (for a panel wider than the sprite) more of its middle, never its far edge.
     */
    private void bodyRows(GuiGraphics graphics, int x, int y, int v, int rows, int w) {
        int first = Math.min(w, BODY_W - CORNER);
        graphics.blit(TEXTURE, x, y, 0, v * S, first * S, rows * S, TEX, TEX);
        int middle = BODY_W - 2 * CORNER;
        for (int done = first; done < w; done += middle) {
            int piece = Math.min(middle, w - done);
            graphics.blit(TEXTURE, x + done * S, y, CORNER * S, v * S, piece * S, rows * S, TEX, TEX);
        }
    }

    /**
     * A strip of the sheet (at {@code u, v}, {@code stripW x stripH} art) laid from art x
     * {@code x0} to {@code x1} at {@code y0}: its start, then its last 2 art pixels as the end.
     */
    private void strip(GuiGraphics graphics, int u, int v, int stripW, int stripH, int x0, int y0, int x1) {
        int body = x1 - x0 - 2;
        int first = Math.min(body, stripW - 2);
        graphics.blit(TEXTURE, sx(x0), sy(y0), u, v, first * S, stripH * S, TEX, TEX);
        // Longer than the sprite: more of its middle (past its 2-pixel start), never its end.
        for (int done = first; done < body; done += stripW - 4) {
            int piece = Math.min(stripW - 4, body - done);
            graphics.blit(TEXTURE, sx(x0 + done), sy(y0), u + 2 * S, v, piece * S, stripH * S, TEX, TEX);
        }
        graphics.blit(TEXTURE, sx(x1 - 2), sy(y0), u + (stripW - 2) * S, v, 2 * S, stripH * S, TEX, TEX);
    }

    /** No world blur behind this screen (the Menu Background Blurriness option): the block being set stays in view. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (Window window : windows) {
            if (window.box.isFocused()) {
                graphics.renderOutline(sx(window.x0) - 1, sy(window.y0) - 1, (window.x1 - window.x0) * S + 2, WINDOW_H * S + 2, BRASS);
            }
        }

        int w = panelW();
        fitted(graphics, title, sx(w / 2.0), sy(5) + 6, (w - 24) * S, TITLE, 0);
        if (!dialOnly) {
            fitted(graphics, Lang.component("telephone.screen.this_phone"), sx(CAPTION_X), sy(18) + 5, (FIELD_X - CAPTION_X - 2) * S, LABEL, -1);
            graphics.drawString(font, "-", sx((FIELD_X + AREA_W + NUMBER_X) / 2.0) - font.width("-") / 2, sy(18) + 5, HINT, false);
            fitted(graphics, Lang.component("telephone.label"), sx(CAPTION_X), sy(30) + 5, (FIELD_X - CAPTION_X - 2) * S, LABEL, -1);
        }
        int calls = callsRow();
        fitted(graphics, Lang.component("telephone.screen.calls"), sx(CAPTION_X), sy(calls) + 5, (FIELD_X - CAPTION_X - 2) * S, LABEL, -1);
        graphics.drawString(font, "-", sx((FIELD_X + AREA_W + NUMBER_X) / 2.0) - font.width("-") / 2, sy(calls) + 5, HINT, false);
        fitted(graphics, Lang.component("telephone.screen.area"), sx(FIELD_X + AREA_W / 2.0), sy(calls - 5), (AREA_W + 6) * S, HINT, 0);
        fitted(graphics, Lang.component("telephone.screen.number"), sx((NUMBER_X + COLUMN_END) / 2.0), sy(calls - 5), (COLUMN_END - NUMBER_X) * S, HINT, 0);

        if (radioDial != null) {
            fitted(graphics, Lang.component("telephone.screen.radio"), sx(RADIO_X + 16), sy(18), 32 * S, LABEL, 0);
            int captionW = (RADIO_END - RADIO_FIELD_X) * S;
            fitted(graphics, Lang.component("telephone.screen.band_start"), sx(RADIO_FIELD_X), sy(BAND_ROW - 5), captionW, HINT, -1);
            fitted(graphics, Lang.component("telephone.screen.band_width"), sx(RADIO_FIELD_X), sy(WIDTH_ROW - 5), captionW, HINT, -1);
            fitted(graphics, Lang.component("telephone.screen.tx_level"), sx(RADIO_FIELD_X), sy(LEVEL_ROW - 5), captionW, HINT, -1);
            fitted(graphics, Lang.component("telephone.screen.channel_range", RadioTuning.MIN_CHANNEL, RadioTuning.MAX_CHANNEL),
                    sx(RADIO_END), sy(BAND_ROW) + 5, (RADIO_END - RADIO_FIELD_X - 24) * S, HINT, 1);

            int start = radioDial.channel();
            int y = sy(READOUT_ROW) + 5;
            graphics.drawString(font, Lang.component("telephone.screen.band_range", start, start + typedWidth() - 1),
                    sx(RADIO_X) + 5, y, READOUT, false);
            fitted(graphics, Lang.component("telephone.screen.reach_short", typedLevel() * 40), sx(RADIO_END) - 5, y,
                    (RADIO_END - RADIO_X) * S / 2 - 6, READOUT, 1);

            // The RTI strip: the route's caption, and on the LCD the ping's answer or the route's state.
            fitted(graphics, Lang.component("telephone.screen.route"), sx(CAPTION_X), sy(rtiRow1()) + 4,
                    (ROUTE_X - CAPTION_X - 2) * S, LABEL, -1);
            RadioRouting phone = routing();
            if (phone != null) {
                int lcdX0 = sx(RTI_LCD_X0) + 5, lcdX1 = sx(rtiLcdEnd()) - 5;
                fitted(graphics, phone.routingReadout(), lcdX0, sy(rtiRow2() + 1) + 5, lcdX1 - lcdX0, READOUT, -1);
            }
        }
    }

    /**
     * {@code text} at {@code y}, aligned on {@code x} (-1 starts there, 0 centres, 1 ends there),
     * scaled down only if it is wider than {@code maxWidth} (some languages run long).
     */
    private void fitted(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int colour, int align) {
        int textW = font.width(text);
        float scale = textW > maxWidth ? maxWidth / (float) textW : 1f;
        float drawnW = textW * scale;
        float x0 = align < 0 ? x : align == 0 ? x - drawnW / 2 : x - drawnW;
        graphics.pose().pushPose();
        graphics.pose().translate(x0, y + (1 - scale) * 4, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, colour, false);
        graphics.pose().popPose();
    }

    /** The number in the calls windows, formatted {@code AAA-NNNNNN}, or empty. */
    private String dialTarget() {
        String dialNumber = dialNumberBox.getValue();
        return dialNumber.isEmpty() ? "" : TelephoneNumbers.formatNumber(parseArea(dialAreaBox.getValue()), dialNumber);
    }

    @Override
    public void removed() {
        String dialTarget = dialTarget();
        if (dialOnly) {
            PacketDistributor.sendToServer(new TelephoneDialPacket(pos, dialTarget));
            return;
        }
        PacketDistributor.sendToServer(new TelephoneSettingsPacket(pos, parseArea(areaCodeBox.getValue()),
                numberBox.getValue(), labelBox.getValue(), dialTarget, autoAnswerLamp.lit,
                pulseLamp != null && pulseLamp.lit));
        if (radioDial != null) {
            PacketDistributor.sendToServer(new RadioTuningPacket(pos, radioDial.channel(), typedLevel(), typedWidth()));
            PacketDistributor.sendToServer(new RadioKeepLoadedPacket(pos, keepLoadedLamp.lit));
        }
    }

    private static int parseArea(String value) {
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- widgets ------------------------------------------------------------------------

    /** An on/off setting: the phone's green lamp, lit when on, with its name beside it (click either). */
    private final class Lamp extends AbstractButton {
        private boolean lit;

        Lamp(int x, int y, int maxWidth, Component name, boolean lit) {
            super(x, y, Math.min(maxWidth, 10 * S + font.width(name)), 8 * S, name);
            this.lit = lit;
        }

        @Override
        public void onPress() {
            lit = !lit;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(TEXTURE, getX(), getY(), 400, lit ? 18 : 0, 8 * S, 8 * S, TEX, TEX);
            fitted(graphics, getMessage(), getX() + 10 * S, getY() + 4, getWidth() - 10 * S,
                    isHoveredOrFocused() ? TITLE : LABEL, -1);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** A route: one of three lamps, lit for the route the phone is on; clicking one picks it. */
    private final class RouteLamp extends AbstractButton {
        private final int index;

        RouteLamp(int x, int y, int index, Component name) {
            super(x, y, (ROUTE_SLOT - 2) * S, 8 * S, name);
            this.index = index;
        }

        @Override
        public void onPress() {
            if (route != index) {
                route = index;
                sendRoute(false);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(TEXTURE, getX(), getY(), 400, route == index ? 18 : 0, 8 * S, 8 * S, TEX, TEX);
            fitted(graphics, getMessage(), getX() + 10 * S, getY() + 4, getWidth() - 10 * S, isHoveredOrFocused() ? TITLE : LABEL, -1);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /**
     * Lock RTI: a key-switch. Turned, the phone uses only the interconnect whose ID is in the window
     * beside it (when its wire reaches it); turned back, the nearest. With no ID typed it won't turn.
     */
    private final class KeySwitch extends AbstractButton {
        KeySwitch(int x, int y) {
            super(x, y, (RTI_ID_X0 - RTI_KEY_X - 2) * S, RTI_KEY * S, Lang.component("telephone.screen.rti_lock"));
        }

        @Override
        public void onPress() {
            if (rtiLock != RadioRouting.NO_LOCK) {
                rtiLock = RadioRouting.NO_LOCK;
            } else if (typedRtiId() != RadioRouting.NO_LOCK) {
                rtiLock = typedRtiId();
            } else {
                return; // nothing to lock to
            }
            sendRoute(false);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean locked = rtiLock != RadioRouting.NO_LOCK;
            graphics.blit(TEXTURE, getX(), getY(), locked ? RTI_KEY_LOCKED_U : RTI_KEY_U, RTI_KEY_V, RTI_KEY * S, RTI_KEY * S, TEX, TEX);
            fitted(graphics, getMessage(), getX() + (RTI_KEY + 2) * S, getY() + 6, getWidth() - (RTI_KEY + 2) * S,
                    isHoveredOrFocused() ? TITLE : LABEL, -1);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** A paper key that does something other than close the screen (PING RTI). */
    private final class ActionKey extends AbstractButton {
        private final Runnable action;

        ActionKey(int x, int y, Component name, Runnable action) {
            super(x, y, KEY_W * S, KEY_H * S, name);
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(TEXTURE, getX(), getY(), 400, isHoveredOrFocused() ? 64 : 40, getWidth(), getHeight(), TEX, TEX);
            fitted(graphics, getMessage(), getX() + getWidth() / 2, getY() + 5, getWidth() - 8, KEY_TEXT, 0);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /**
     * Dial: works the line as lifting the handset does, with the number in the windows. The paper
     * key with the phone's green lamp let in: OFF (raised, lamp dark) while the line is free, ON
     * (pressed in, lamp lit) while it is in use, when it reads Hang up.
     */
    private final class DialKey extends AbstractButton {
        private Boolean shown;

        DialKey(int x, int y) {
            super(x, y, KEY_W * S, KEY_H * S, Lang.component("telephone.screen.dial_key"));
        }

        @Override
        public void onPress() {
            PacketDistributor.sendToServer(new TelephoneDialPacket(pos, dialTarget(), true));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean on = TelephoneClient.lineInUse(pos);
            if (!Boolean.valueOf(on).equals(shown)) {
                shown = on;
                setMessage(Lang.component(on ? "telephone.screen.hang_up_key" : "telephone.screen.dial_key"));
                setTooltip(Tooltip.create(Lang.component(on ? "telephone.screen.hang_up_tooltip" : "telephone.screen.dial_tooltip")));
            }
            boolean hot = isHoveredOrFocused();
            int v = on ? (hot ? DIAL_ON_HOVER_V : DIAL_ON_V) : (hot ? DIAL_OFF_HOVER_V : DIAL_OFF_V);
            graphics.blit(TEXTURE, getX(), getY(), DIAL_U, v, getWidth(), getHeight(), TEX, TEX);
            int textX0 = getX() + DIAL_LAMP_END * S, textX1 = getX() + getWidth() - 2 * S;
            fitted(graphics, getMessage(), (textX0 + textX1) / 2, getY() + 5 + (on ? 1 : 0), textX1 - textX0 - 4, KEY_TEXT, 0);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Done: a paper key, rimmed in brass under the mouse. */
    private final class Key extends AbstractButton {
        Key(int x, int y, Component name) {
            super(x, y, KEY_W * S, KEY_H * S, name);
        }

        @Override
        public void onPress() {
            onClose();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(TEXTURE, getX(), getY(), 400, isHoveredOrFocused() ? 64 : 40, getWidth(), getHeight(), TEX, TEX);
            fitted(graphics, getMessage(), getX() + getWidth() / 2, getY() + 5, getWidth() - 8, KEY_TEXT, 0);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
