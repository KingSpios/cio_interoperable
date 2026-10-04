package com.cio.createinteroperable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter.ScrollOptionSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The in-world Auto-Answer toggle on all three CIO telephones (one shared class
 * instead of the three private copies it replaces). Two Create behaviours
 * sharing a block entity collide in three separate places unless each is made
 * distinct, and the old Auto-Answer + Area Code pair collided in all three:
 * <ol>
 *   <li><b>{@link #getType()}</b> &mdash; {@code SmartBlockEntity} keys its
 *       behaviours by type, and {@code ScrollValueBehaviour.TYPE} is one static
 *       for the whole class, so a second slider silently evicted the first.</li>
 *   <li><b>{@link #netId()}</b> &mdash; Create's {@code ValueSettingsPacket}
 *       applies a saved value to the <em>first</em> behaviour whose
 *       {@code netId()} matches, and the default is 0 for every one. The
 *       Auto-Answer choice was applied to whichever slider happened to come
 *       first, so the toggle often didn't flip.</li>
 *   <li><b>NBT</b> &mdash; {@code ScrollValueBehaviour} saves under the fixed
 *       key {@code ScrollValue}; both sliders wrote it and both read back
 *       whichever wrote last. Auto-Answer could come back holding an area code
 *       (e.g. 123), and Create's settings screen then parks the cursor at that
 *       value's coordinate on a two-option board, far off the screen's edge.</li>
 * </ol>
 * The Area Code slider is gone (the telephone settings screen sets it), and
 * this one now has its own type, net id and NBT key.
 */
public class TelephoneAutoAnswerBehaviour extends ScrollValueBehaviour {

    public static final BehaviourType<TelephoneAutoAnswerBehaviour> TYPE = new BehaviourType<>();
    /** Anything but 0, which every other value-settings behaviour defaults to. */
    private static final int NET_ID = 0x7E1E;
    private static final String NBT_KEY = "AutoAnswer";
    private static final String LEGACY_NBT_KEY = "ScrollValue";

    private static final INamedIconOptions[] OPTIONS = {
            iconOption(AllIcons.I_DISABLE, "OFF"),
            iconOption(AllIcons.I_ACTIVE, "ON"),
    };

    public TelephoneAutoAnswerBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
        super(Component.literal("Auto-Answer"), be, slot);
        between(0, 1);
        withFormatter(i -> CIOGlyphs.onOff(i != 0));
    }

    public boolean isOn() {
        return getValue() == 1;
    }

    public void setOn(boolean on) {
        setValue(on ? 1 : 0);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public int netId() {
        return NET_ID;
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Component.literal("Auto")),
                new ScrollOptionSettingsFormatter(OPTIONS));
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.remove(LEGACY_NBT_KEY);
        nbt.putInt(NBT_KEY, value);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket); // reads the legacy key, if any
        if (nbt.contains(NBT_KEY)) {
            value = nbt.getInt(NBT_KEY);
        }
        // A legacy value above 1 was really the area code slider's.
        value = value == 1 ? 1 : 0;
    }

    /** The area code a pre-fix save left under the shared legacy key, if it plausibly was one. */
    public static int legacyAreaCode(CompoundTag tag) {
        int legacy = tag.getInt(LEGACY_NBT_KEY);
        return legacy > 1 && legacy <= 999 ? legacy : 0;
    }

    private static INamedIconOptions iconOption(AllIcons icon, String label) {
        return new INamedIconOptions() {
            @Override
            public AllIcons getIcon() {
                return icon;
            }

            @Override
            public String getTranslationKey() {
                return label; // rendered verbatim when not a registered key
            }
        };
    }
}
