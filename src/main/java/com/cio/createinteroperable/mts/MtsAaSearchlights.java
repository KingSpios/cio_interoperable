package com.cio.createinteroperable.mts;

import com.cio.createinteroperable.CIOBlocks;
import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.mixin.mts.WrapperEntityAccessor;
import com.cio.createinteroperable.mixin.mts.WrapperWorldAccessor;
import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.ComputedVariable;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import minecrafttransportsimulator.entities.instances.APart;
import minecrafttransportsimulator.entities.instances.EntityPlacedPart;
import minecrafttransportsimulator.jsondefs.JSONAction;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makes the MTS Official Content Pack's AA Spotlight need Domestic Electrical
 * Board power &mdash; gated at the AA Base Plate it's mounted on, since IV's
 * placed parts are entities and the plate is what gets wired.
 *
 * <p><b>Plate side</b> ({@link #tickPart}, server, from every part's own tick):
 * a ground-placed {@code mtsofficialpack:aa_base} keeps a
 * {@link MtsAaPowerNodeBlock} under one of its four corners (whichever corner
 * cell is free, starting with the one opposite the ammo-crate slot), reads that
 * node's power, and publishes it as the plate's own IV variable
 * {@value #POWERED_VAR} (1 = powered). IV syncs it to clients and saves it with
 * the entity, so both sides gate on the same value &mdash; and a pack animation
 * could even read it (as {@code parent_cio_powered} from the Spotlight). With no
 * power it holds the Spotlight's {@code switch} (beam) and {@code spin}
 * (auto-rotate) at 0, so a power cut kills a lit beam at once, and tells the node
 * what the lamp is asking for so the board can bill it.</p>
 *
 * <p><b>Spotlight side</b> ({@link #isLockedOut}, both sides): a
 * {@code mtsofficialpack:spotlight} on a ground-placed plate whose
 * {@value #POWERED_VAR} is 0 refuses to switch its beam or auto-rotate on
 * ({@link #blocksAction}, server), doesn't traverse or elevate at all
 * ({@code PartGun#handleMovement} is skipped, both sides), and has every light
 * zeroed &mdash; including the amber standby LED, which is lit whenever the
 * lamp is merely switched off ({@link #dimLights}, client). The beam's sounds
 * key off {@code switch} and the servo whine off {@code gun_yawing}, so they
 * stop on their own.</p>
 *
 * <p>Spotlights on vehicles, and anything else on the plate (the two AA guns,
 * the ammo crate), are left alone. Only ever loaded with Immersive Vehicles
 * present: its callers are the {@code mixin.mts} mixins
 * ({@code MtsMixinPlugin}-gated) and the client HUD registered behind
 * {@code ImmersiveVehiclesCompat.present()}.</p>
 */
public final class MtsAaSearchlights {

    public static final String PACK_ID = "mtsofficialpack";
    public static final String BASE_ID = "aa_base";
    public static final String SPOTLIGHT_ID = "spotlight";

    /** Plate variable: 1 while a live rail reaches its node (or the gate is disabled). */
    public static final String POWERED_VAR = "cio_powered";
    private static final String SWITCH_VAR = "switch";
    private static final String SPIN_VAR = "spin";

    public static final String NO_POWER_KEY = "createinteroperable.mts.aa_spotlight.no_power";

    /**
     * Plate-local nub positions, one per corner of the 2&times;2 plate, tried in
     * order. The plate's ammo-crate slot sits at local (-1.125, 1.0), so its
     * corner comes last.
     */
    private static final double[][] CORNERS = {
            {0.75, -0.75}, {-0.75, -0.75}, {0.75, 0.75}, {-0.75, 0.75}};

    private static final double NUB_MARGIN = 0.125;

    /** Last node cell per live plate (server thread only). */
    private static final Map<APart, BlockPos> NODE_CACHE = new WeakHashMap<>();

    private MtsAaSearchlights() {
    }

    // --- identity ---------------------------------------------------------

    private static boolean is(AEntityD_Definable<?> entity, String systemName) {
        return entity.definition != null
                && PACK_ID.equals(entity.definition.packID)
                && systemName.equals(entity.definition.systemName);
    }

    /** An AA Base Plate standing on the ground (not mounted on a vehicle). */
    public static boolean isGroundBase(AEntityD_Definable<?> entity) {
        return entity instanceof APart part && part.entityOn instanceof EntityPlacedPart && is(part, BASE_ID);
    }

    public static boolean isSpotlight(AEntityD_Definable<?> entity) {
        return entity instanceof APart && is(entity, SPOTLIGHT_ID);
    }

    /** The ground-placed plate this Spotlight is mounted on, or null. */
    @Nullable
    public static APart groundBaseOf(AEntityD_Definable<?> entity) {
        if (entity instanceof APart part && isSpotlight(part) && part.partOn != null && isGroundBase(part.partOn)) {
            return part.partOn;
        }
        return null;
    }

    public static boolean isBasePowered(APart base) {
        return base.getOrCreateVariable(POWERED_VAR).isActive;
    }

    /** True for a Spotlight on a ground-placed plate that has no power. */
    public static boolean isLockedOut(AEntityD_Definable<?> entity) {
        APart base = groundBaseOf(entity);
        return base != null && !isBasePowered(base);
    }

    // --- spotlight gates -------------------------------------------------

    /** Server: refuse any action that would switch the beam or auto-rotate ON without power. */
    public static boolean blocksAction(AEntityD_Definable<?> entity, @Nullable JSONAction action, boolean conditionsTrue) {
        if (!conditionsTrue || action == null || !isGatedVariable(action.variable) || !isLockedOut(entity)) {
            return false;
        }
        return !entity.getOrCreateVariable(action.variable).isActive;
    }

    private static boolean isGatedVariable(@Nullable String variable) {
        return SWITCH_VAR.equals(variable) || SPIN_VAR.equals(variable);
    }

    /**
     * Server: a right-click on the Spotlight's beam button or spin lever while
     * unpowered gets the action-bar "Missing power" hint (the click itself is
     * refused by {@link #blocksAction}).
     */
    public static void warnIfLockedOut(AEntityE_Interactable<?> entity, Point3D hitBoxLocalCenter, IWrapperPlayer player) {
        if (!isLockedOut(entity)) {
            return;
        }
        for (BoundingBox box : entity.collisionBoxes) {
            if (box.localCenter.equals(hitBoxLocalCenter)) {
                if (box.definition != null && box.definition.action != null
                        && isGatedVariable(box.definition.action.variable)
                        && player instanceof WrapperEntityAccessor accessor
                        && accessor.cio$getEntity() instanceof Player mcPlayer) {
                    mcPlayer.displayClientMessage(Component.translatable(NO_POWER_KEY), true);
                }
                return;
            }
        }
    }

    /** Client, after IV computes this frame's light levels: black out an unpowered Spotlight. */
    public static void dimLights(AEntityD_Definable<?> entity) {
        if (!entity.lightBrightnessValues.isEmpty() && isLockedOut(entity)) {
            entity.lightBrightnessValues.replaceAll((light, brightness) -> 0.0F);
        }
    }

    // --- plate side -----------------------------------------------------

    /** Server, end of every IV part's tick. Only ground-placed AA Base Plates do anything. */
    public static void tickPart(APart part) {
        if (part.world.isClient() || !isGroundBase(part)) {
            return;
        }
        Level level = levelOf(part.world);
        if (level == null) {
            return;
        }
        boolean enabled = CIOConfig.MTS_AA_SPOTLIGHT_REQUIRES_POWER.get();
        MtsAaPowerNodeBlockEntity node = findOrPlaceNode(level, part);
        boolean powered = !enabled || (node != null && node.appliancePowered());
        part.getOrCreateVariable(POWERED_VAR).setTo(powered ? 1.0 : 0.0, true);

        boolean beam = false;
        boolean servo = false;
        for (APart child : part.parts) {
            if (!isSpotlight(child)) {
                continue;
            }
            ComputedVariable switchVar = child.getOrCreateVariable(SWITCH_VAR);
            ComputedVariable spinVar = child.getOrCreateVariable(SPIN_VAR);
            if (!powered) {
                switchVar.setTo(0.0, true);
                spinVar.setTo(0.0, true);
            }
            beam |= switchVar.isActive;
            servo |= spinVar.isActive;
        }
        if (node != null) {
            node.markBaseSeen(level.getGameTime());
            node.setDemand(enabled && beam, enabled && servo);
        }
    }

    /**
     * This plate's node: the cached cell first, then any corner cell already
     * holding one bound to this plate, and &mdash; once a second, so a blocked
     * plate doesn't scan every tick &mdash; a fresh one in the first free corner
     * cell (air or a replaceable plant, never a fluid or another plate's node).
     */
    @Nullable
    private static MtsAaPowerNodeBlockEntity findOrPlaceNode(Level level, APart base) {
        if (CIOBlocks.MTS_AA_POWER_NODE == null) {
            return null;
        }
        BlockPos baseCell = BlockPos.containing(base.position.x, base.position.y + 0.01, base.position.z);
        BlockPos cached = NODE_CACHE.get(base);
        if (cached != null) {
            MtsAaPowerNodeBlockEntity node = boundNodeAt(level, cached, baseCell);
            if (node != null) {
                return node;
            }
            NODE_CACHE.remove(base);
        }

        BlockPos[] cells = new BlockPos[CORNERS.length];
        double[][] nubs = new double[CORNERS.length][];
        for (int i = 0; i < CORNERS.length; i++) {
            Point3D corner = new Point3D(CORNERS[i][0], 0.0, CORNERS[i][1]).rotate(base.orientation).add(base.position);
            cells[i] = BlockPos.containing(corner.x, base.position.y + 0.01, corner.z);
            nubs[i] = new double[]{
                    clampNub(corner.x - cells[i].getX()),
                    clampNub(corner.z - cells[i].getZ())};
            MtsAaPowerNodeBlockEntity node = boundNodeAt(level, cells[i], baseCell);
            if (node != null) {
                node.bind(baseCell, nubs[i][0], nubs[i][1]);
                NODE_CACHE.put(base, cells[i]);
                return node;
            }
        }

        if (level.getGameTime() % 20L != 0L) {
            return null;
        }
        for (int i = 0; i < cells.length; i++) {
            BlockPos cell = cells[i];
            if (!level.isLoaded(cell) || level.getBlockEntity(cell) instanceof MtsAaPowerNodeBlockEntity) {
                continue;
            }
            BlockState state = level.getBlockState(cell);
            if (!(state.isAir() || state.canBeReplaced()) || !state.getFluidState().isEmpty()) {
                continue;
            }
            level.setBlock(cell, CIOBlocks.MTS_AA_POWER_NODE.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(cell) instanceof MtsAaPowerNodeBlockEntity node) {
                node.bind(baseCell, nubs[i][0], nubs[i][1]);
                NODE_CACHE.put(base, cell);
                return node;
            }
        }
        return null;
    }

    @Nullable
    private static MtsAaPowerNodeBlockEntity boundNodeAt(Level level, BlockPos cell, BlockPos baseCell) {
        if (level.isLoaded(cell) && level.getBlockEntity(cell) instanceof MtsAaPowerNodeBlockEntity node
                && baseCell.equals(node.basePos())) {
            return node;
        }
        return null;
    }

    private static double clampNub(double local) {
        return Math.max(NUB_MARGIN, Math.min(1.0 - NUB_MARGIN, local));
    }

    @Nullable
    private static Level levelOf(AWrapperWorld world) {
        return world instanceof WrapperWorldAccessor accessor ? accessor.cio$getLevel() : null;
    }
}
