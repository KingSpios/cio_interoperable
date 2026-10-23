package com.cio.createinteroperable;

import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import com.cio.createinteroperable.letsdo.LetsDoLampStates;
import com.cio.createinteroperable.letsdo.SinkBlockEntity;
import com.cio.createinteroperable.letsdo.SinkStates;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/**
 * Block entity types that exist on every install. Types for optional-mod
 * content live in their module (see
 * {@link com.cio.createinteroperable.modules.CIOModules}).
 */
public class CIOBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamOutletBlockEntity>> STEAM_OUTLET =
            BLOCK_ENTITIES.register("steam_outlet", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new SteamOutletBlockEntity(CIOBlockEntities.STEAM_OUTLET.get(), pos, state),
                    CIOBlocks.STEAM_OUTLET.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrassHeaterBlockEntity>> BRASS_HEATER =
            BLOCK_ENTITIES.register("brass_heater", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new BrassHeaterBlockEntity(CIOBlockEntities.BRASS_HEATER.get(), pos, state),
                    CIOBlocks.BRASS_HEATER.get()).build(null));

    // Multi Radiator — see CIOBlocks.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadiatorValveNorthBlockEntity>> RADIATOR_VALVE_NORTH =
            BLOCK_ENTITIES.register("multi_radiator_north", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new RadiatorValveNorthBlockEntity(CIOBlockEntities.RADIATOR_VALVE_NORTH.get(), pos, state),
                    CIOBlocks.RADIATOR_VALVE_NORTH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadiatorValveSouthBlockEntity>> RADIATOR_VALVE_SOUTH =
            BLOCK_ENTITIES.register("multi_radiator_south", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new RadiatorValveSouthBlockEntity(CIOBlockEntities.RADIATOR_VALVE_SOUTH.get(), pos, state),
                    CIOBlocks.RADIATOR_VALVE_SOUTH.get()).build(null));

    // Minimal, goggle-info-only BlockEntity — see RadiatorMiddleBlockEntity's
    // own doc for why a middle segment needs one at all.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadiatorMiddleBlockEntity>> RADIATOR_MIDDLE =
            BLOCK_ENTITIES.register("multi_radiator_middle", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new RadiatorMiddleBlockEntity(CIOBlockEntities.RADIATOR_MIDDLE.get(), pos, state),
                    CIOBlocks.RADIATOR_MIDDLE.get()).build(null));

    // Attached (via com.cio.createinteroperable.mixin.letsdo.FarmAndCharmSinkBlockMixin)
    // to every registered Let's Do kitchen sink so Create's pipe system will look
    // for a fluid handler there at all — see
    // com.cio.createinteroperable.letsdo.SinkBlockEntity. The valid-blocks list is
    // scanned from the registry when this supplier runs (block-entity types
    // register after blocks); it is empty — and this type simply never matches —
    // when no Let's Do sink mod is installed.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SinkBlockEntity>> LETSDO_SINK =
            BLOCK_ENTITIES.register("letsdo_sink", () -> {
                Set<Block> sinks = SinkStates.sinkBlocks();
                return BlockEntityType.Builder
                        .of(SinkBlockEntity::new, sinks.toArray(new Block[0]))
                        .build(null);
            });

    // Attached (via com.cio.createinteroperable.mixin.letsdo.LetsDoLampBlockMixin)
    // to every registered Let's Do Furniture / Candlelight lamp + street-lantern
    // block — see LetsDoLampBlockEntity. Empty valid-blocks (type never matches)
    // when none of those mods are installed.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LetsDoLampBlockEntity>> LETSDO_LAMP =
            BLOCK_ENTITIES.register("letsdo_lamp", () -> {
                Set<Block> lamps = LetsDoLampStates.lampBlocks();
                return BlockEntityType.Builder
                        .of(LetsDoLampBlockEntity::new, lamps.toArray(new Block[0]))
                        .build(null);
            });

    // Aircon top + venter — backend-agnostic, see CIOBlocks.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirconMotorTopBlockEntity>> AIRCON_MOTOR_TOP =
            BLOCK_ENTITIES.register("aircon_motor_top", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new AirconMotorTopBlockEntity(CIOBlockEntities.AIRCON_MOTOR_TOP.get(), pos, state),
                    CIOBlocks.AIRCON_MOTOR_TOP.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirconVenterBlockEntity>> AIRCON_VENTER =
            BLOCK_ENTITIES.register("aircon_venter", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new AirconVenterBlockEntity(CIOBlockEntities.AIRCON_VENTER.get(), pos, state),
                    CIOBlocks.AIRCON_VENTER.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
