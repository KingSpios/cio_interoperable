package com.cio.createinteroperable;

import com.cio.createinteroperable.copycat.CopycatEncasedPipeBlockEntity;
import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import com.cio.createinteroperable.letsdo.LetsDoLampStates;
import com.cio.createinteroperable.letsdo.SinkBlockEntity;
import com.cio.createinteroperable.letsdo.SinkStates;
import com.cio.createinteroperable.thermal.AirconMotorTopBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanBathtubBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanDishwasherBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanDryerBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanFanBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanFixtureBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanLanternBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanToiletBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanFixtures;
import com.cio.createinteroperable.urbandecor.UrbanRadiatorBlockEntity;
import com.cio.createinteroperable.urbandecor.UrbanWashingMachineBlockEntity;
import com.cio.createinteroperable.thermal.AirconVenterBlockEntity;
import com.cio.createinteroperable.thermal.BrassHeaterBlockEntity;
import com.cio.createinteroperable.thermal.RadiatorMiddleBlockEntity;
import com.cio.createinteroperable.thermal.RadiatorValveNorthBlockEntity;
import com.cio.createinteroperable.thermal.RadiatorValveSouthBlockEntity;
import com.cio.createinteroperable.thermal.SteamOutletBlockEntity;
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

    // Create's own pipe block entity (fluid transport) plus the pipe kind and
    // its casing, for the encased pipe a Steam Engine mounts on — see SteamEnginePipeBlock.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopycatEncasedPipeBlockEntity>> STEAM_ENGINE_PIPE =
            BLOCK_ENTITIES.register("steam_engine_pipe", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CopycatEncasedPipeBlockEntity(CIOBlockEntities.STEAM_ENGINE_PIPE.get(), pos, state),
                    CIOBlocks.STEAM_ENGINE_PIPE.get()).build(null));

    // Create's pipe block entity plus the casing's copied block — see CopycatEncasedPipeBlock.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopycatEncasedPipeBlockEntity>> COPYCAT_ENCASED_PIPE =
            BLOCK_ENTITIES.register("copycat_encased_pipe", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CopycatEncasedPipeBlockEntity(CIOBlockEntities.COPYCAT_ENCASED_PIPE.get(), pos, state),
                    CIOBlocks.COPYCAT_ENCASED_PIPE.get()).build(null));

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

    // Attached (via com.cio.createinteroperable.mixin.urbandecor.UrbanFixtureBlockMixin)
    // to Urban Decor's sink / faucet / shower so Create's pipes have somewhere to
    // deliver — see com.cio.createinteroperable.urbandecor.UrbanFixtureBlockEntity.
    // Scanned from the registry like LETSDO_SINK: empty (never matches) without Urban Decor.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanFixtureBlockEntity>> URBAN_FIXTURE =
            BLOCK_ENTITIES.register("urban_fixture", () -> BlockEntityType.Builder
                    .of(UrbanFixtureBlockEntity::new, UrbanFixtures.fixtureBlocks().toArray(new Block[0]))
                    .build(null));

    // The Urban Decor toilet's cistern — see UrbanToiletBlockEntity.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanToiletBlockEntity>> URBAN_TOILET =
            BLOCK_ENTITIES.register("urban_toilet", () -> BlockEntityType.Builder
                    .of(UrbanToiletBlockEntity::new, UrbanFixtures.toiletBlocks().toArray(new Block[0]))
                    .build(null));

    // The Urban Decor washing machine — see UrbanWashingMachineBlockEntity.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanWashingMachineBlockEntity>> URBAN_WASHER =
            BLOCK_ENTITIES.register("urban_washer", () -> BlockEntityType.Builder
                    .of(UrbanWashingMachineBlockEntity::new, UrbanFixtures.washerBlocks().toArray(new Block[0]))
                    .build(null));

    // The Urban Decor dryer — the washing machine's drum without the water, see UrbanDryerBlockEntity.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanDryerBlockEntity>> URBAN_DRYER =
            BLOCK_ENTITIES.register("urban_dryer", () -> BlockEntityType.Builder
                    .of(UrbanDryerBlockEntity::new, UrbanFixtures.dryerBlocks().toArray(new Block[0]))
                    .build(null));

    // The Urban Decor dishwasher - the washing machine's drum for tools, see UrbanDishwasherBlockEntity.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanDishwasherBlockEntity>> URBAN_DISHWASHER =
            BLOCK_ENTITIES.register("urban_dishwasher", () -> BlockEntityType.Builder
                    .of(UrbanDishwasherBlockEntity::new, UrbanFixtures.dishwasherBlocks().toArray(new Block[0]))
                    .build(null));

    // The Urban Decor desk fan — see UrbanFanBlockEntity.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanFanBlockEntity>> URBAN_FAN =
            BLOCK_ENTITIES.register("urban_fan", () -> BlockEntityType.Builder
                    .of(UrbanFanBlockEntity::new, UrbanFixtures.fanBlocks().toArray(new Block[0]))
                    .build(null));

    // The Urban Decor stainless steel lanterns' power node — see UrbanLanternBlockEntity.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanLanternBlockEntity>> URBAN_LANTERN =
            BLOCK_ENTITIES.register("urban_lantern", () -> BlockEntityType.Builder
                    .of(UrbanLanternBlockEntity::new, UrbanFixtures.lanternBlocks().toArray(new Block[0]))
                    .build(null));

    // The Urban Decor radiator as a small Steam Hearth — see UrbanRadiatorBlockEntity.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanRadiatorBlockEntity>> URBAN_RADIATOR =
            BLOCK_ENTITIES.register("urban_radiator", () -> BlockEntityType.Builder
                    .of(UrbanRadiatorBlockEntity::new, UrbanFixtures.radiatorBlocks().toArray(new Block[0]))
                    .build(null));

    // Both halves of an Urban Decor bathtub — see UrbanBathtubBlockEntity.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UrbanBathtubBlockEntity>> URBAN_BATHTUB =
            BLOCK_ENTITIES.register("urban_bathtub", () -> BlockEntityType.Builder
                    .of(UrbanBathtubBlockEntity::new, UrbanFixtures.bathtubBlocks().toArray(new Block[0]))
                    .build(null));

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
