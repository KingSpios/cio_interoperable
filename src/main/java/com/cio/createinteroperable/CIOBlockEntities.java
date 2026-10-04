package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.IdenDecorCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import com.cio.createinteroperable.deb.CeeDebRectifierBlockEntity;
import com.cio.createinteroperable.deb.CeePowerKitTier1BlockEntity;
import com.cio.createinteroperable.deb.CeePowerKitTier3BlockEntity;
import com.cio.createinteroperable.deb.CeePowerKitTier4BlockEntity;
import com.cio.createinteroperable.deb.DebRectifierBlockEntity;
import com.cio.createinteroperable.deb.PowerKitTier1BlockEntity;
import com.cio.createinteroperable.deb.PowerKitTier3BlockEntity;
import com.cio.createinteroperable.deb.PowerKitTier4BlockEntity;
import com.cio.createinteroperable.iden.ElectricSwitchBlockEntity;
import com.cio.createinteroperable.iden.IdenCeeTelephoneBlockEntity;
import com.cio.createinteroperable.iden.IdenPgTelephoneBlockEntity;
import com.cio.createinteroperable.iden.IdenTelephones;
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
import org.patryk3211.powergrid.electricity.wireconnector.ConnectorBlockEntity;

import java.util.Set;

public class CIOBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);

    // Mirrors CIOBlocks' PG/CEE/BOTH gates exactly — a BlockEntityType is only
    // ever registered when at least one block using it was.
    private static final boolean PG = PowerGridCompat.present();
    private static final boolean CEE = ElectroEnergeticsCompat.present();
    private static final boolean BOTH = PG && CEE;

    // Mirrors CIOBlocks.BRIDGE_EXTRAS exactly — see that field's doc.
    private static final boolean BRIDGE_EXTRAS = false;

    // Only the PG column needs a real vanilla BlockEntity — the CEE column
    // uses CEE's own SimulatedDevice/DevicesSavedData system instead (no
    // BlockEntity at all), and the core column has no state to hold.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InteroperablePgAssembledBlockEntity>> PG_ASSEMBLED =
            BRIDGE_EXTRAS && BOTH ? BLOCK_ENTITIES.register("pg_assembled", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InteroperablePgAssembledBlockEntity(CIOBlockEntities.PG_ASSEMBLED.get(), pos, state),
                    CIOBlocks.PG_ASSEMBLED.get()).build(null)) : null;

    // Interim 1x1 bridge block's own BlockEntity — same reasoning as
    // PG_ASSEMBLED above (only the PG-electrical side needs a vanilla
    // BlockEntity; the CEE side is handled by SimulatedDevice/DevicesSavedData).
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InteroperableSmallBlockEntity>> SMALL =
            BRIDGE_EXTRAS && BOTH ? BLOCK_ENTITIES.register("small", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InteroperableSmallBlockEntity(CIOBlockEntities.SMALL.get(), pos, state),
                    CIOBlocks.SMALL.get()).build(null)) : null;

    // Interoperable Coupler — same reasoning as SMALL above (PG-electrical side
    // needs a vanilla BlockEntity; the CEE side is a SimulatedDevice). Reuses
    // InteroperableDevice / CIODevices.INTEROPERABLE unchanged.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InteroperableCouplerBlockEntity>> COUPLER =
            BRIDGE_EXTRAS && BOTH ? BLOCK_ENTITIES.register("coupler", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InteroperableCouplerBlockEntity(CIOBlockEntities.COUPLER.get(), pos, state),
                    CIOBlocks.COUPLER.get()).build(null)) : null;

    // Double Coupler — real current-carrying bridge (2-terminal winding per side).
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InteroperableDoubleCouplerBlockEntity>> DOUBLE_COUPLER =
            BOTH ? BLOCK_ENTITIES.register("double_coupler", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InteroperableDoubleCouplerBlockEntity(CIOBlockEntities.DOUBLE_COUPLER.get(), pos, state),
                    CIOBlocks.DOUBLE_COUPLER.get()).build(null)) : null;

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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TelephoneBlockEntity>> TELEPHONE =
            BOTH ? BLOCK_ENTITIES.register("telephone", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new TelephoneBlockEntity(CIOBlockEntities.TELEPHONE.get(), pos, state),
                    CIOBlocks.TELEPHONE.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CpgTelephoneBlockEntity>> CPG_TELEPHONE =
            PG ? BLOCK_ENTITIES.register("cpg_telephone", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CpgTelephoneBlockEntity(CIOBlockEntities.CPG_TELEPHONE.get(), pos, state),
                    CIOBlocks.CPG_TELEPHONE.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeTelephoneBlockEntity>> CEE_TELEPHONE =
            CEE ? BLOCK_ENTITIES.register("cee_telephone", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeeTelephoneBlockEntity(CIOBlockEntities.CEE_TELEPHONE.get(), pos, state),
                    CIOBlocks.CEE_TELEPHONE.get()).build(null)) : null;

    // --- Aesthetic PG connector reskins (see CIOConnectorBlock/CIOConnectorGlassBlock) ---
    // Reuses PG's own real ConnectorBlockEntity class verbatim (no CIO-side
    // circuit logic needed — a connector is just a 1-terminal pass-through).
    // Same dual-validBlocks pattern PG's own WIRE_CONNECTOR type uses for its
    // light/heavy pair. PG-only.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConnectorBlockEntity>> CONNECTOR =
            BRIDGE_EXTRAS && PG ? BLOCK_ENTITIES.register("connector", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ConnectorBlockEntity(CIOBlockEntities.CONNECTOR.get(), pos, state),
                    CIOBlocks.CIO_CONNECTOR.get(), CIOBlocks.CIO_CONNECTOR_GLASS.get()).build(null)) : null;

    // Double Connector — real, active content (unlike the disabled reskins
    // above): two independent, unconnected terminal points in one block. Own
    // BlockEntity (CIODoubleConnectorBlockEntity, setTerminalCount(2)) since
    // ConnectorBlockEntity itself hardcodes 1.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CIODoubleConnectorBlockEntity>> DOUBLE_CONNECTOR =
            PG ? BLOCK_ENTITIES.register("cpg_double_connector", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CIODoubleConnectorBlockEntity(CIOBlockEntities.DOUBLE_CONNECTOR.get(), pos, state),
                    CIOBlocks.CIO_DOUBLE_CONNECTOR.get()).build(null)) : null;

    // Domestic Electrical Board (PG-only tiers) — its own BlockEntity carries
    // the PG 4-terminal circuit and the Crayfish ISourceNode bookkeeping. The
    // CEE-wired tiers below are a fully separate BlockEntity family
    // (CeeDebRectifierBlockEntity) with no Power Grid type in its hierarchy.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.cio.createinteroperable.deb.PowerKitTier2BlockEntity>> DEB_RECTIFIER =
            PG ? BLOCK_ENTITIES.register("deb_rectifier", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new com.cio.createinteroperable.deb.PowerKitTier2BlockEntity(CIOBlockEntities.DEB_RECTIFIER.get(), pos, state),
                    CIOBlocks.DEB_RECTIFIER.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerKitTier1BlockEntity>> DEB_RECTIFIER_TIER1 =
            PG ? BLOCK_ENTITIES.register("deb_rectifier_tier1", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PowerKitTier1BlockEntity(CIOBlockEntities.DEB_RECTIFIER_TIER1.get(), pos, state),
                    CIOBlocks.DEB_RECTIFIER_TIER1.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerKitTier3BlockEntity>> DEB_RECTIFIER_TIER3 =
            PG ? BLOCK_ENTITIES.register("deb_rectifier_tier3", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PowerKitTier3BlockEntity(CIOBlockEntities.DEB_RECTIFIER_TIER3.get(), pos, state),
                    CIOBlocks.DEB_RECTIFIER_TIER3.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerKitTier4BlockEntity>> DEB_RECTIFIER_TIER4 =
            PG ? BLOCK_ENTITIES.register("deb_rectifier_tier4", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PowerKitTier4BlockEntity(CIOBlockEntities.DEB_RECTIFIER_TIER4.get(), pos, state),
                    CIOBlocks.DEB_RECTIFIER_TIER4.get()).build(null)) : null;

    // CEE-wired Power Kit tiers — own BlockEntity family, CEE-only.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.cio.createinteroperable.deb.CeePowerKitTier2BlockEntity>> CEE_DEB_RECTIFIER =
            CEE ? BLOCK_ENTITIES.register("cee_deb_rectifier", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new com.cio.createinteroperable.deb.CeePowerKitTier2BlockEntity(CIOBlockEntities.CEE_DEB_RECTIFIER.get(), pos, state),
                    CIOBlocks.CEE_DEB_RECTIFIER.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeePowerKitTier1BlockEntity>> CEE_DEB_RECTIFIER_TIER1 =
            CEE ? BLOCK_ENTITIES.register("cee_deb_rectifier_tier1", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeePowerKitTier1BlockEntity(CIOBlockEntities.CEE_DEB_RECTIFIER_TIER1.get(), pos, state),
                    CIOBlocks.CEE_DEB_RECTIFIER_TIER1.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeePowerKitTier3BlockEntity>> CEE_DEB_RECTIFIER_TIER3 =
            CEE ? BLOCK_ENTITIES.register("cee_deb_rectifier_tier3", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeePowerKitTier3BlockEntity(CIOBlockEntities.CEE_DEB_RECTIFIER_TIER3.get(), pos, state),
                    CIOBlocks.CEE_DEB_RECTIFIER_TIER3.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeePowerKitTier4BlockEntity>> CEE_DEB_RECTIFIER_TIER4 =
            CEE ? BLOCK_ENTITIES.register("cee_deb_rectifier_tier4", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeePowerKitTier4BlockEntity(CIOBlockEntities.CEE_DEB_RECTIFIER_TIER4.get(), pos, state),
                    CIOBlocks.CEE_DEB_RECTIFIER_TIER4.get()).build(null)) : null;

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

    // Aircon — see CIOBlocks.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirconMotorBottomBlockEntity>> AIRCON_MOTOR_BOTTOM =
            PG ? BLOCK_ENTITIES.register("aircon_motor_bottom", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new AirconMotorBottomBlockEntity(CIOBlockEntities.AIRCON_MOTOR_BOTTOM.get(), pos, state),
                    CIOBlocks.AIRCON_MOTOR_BOTTOM.get(), CIOBlocks.AIRCON_MOTOR_BOTTOM_240.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeAirconMotorBottomBlockEntity>> CEE_AIRCON_MOTOR_BOTTOM =
            CEE ? BLOCK_ENTITIES.register("cee_aircon_motor_bottom", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeeAirconMotorBottomBlockEntity(CIOBlockEntities.CEE_AIRCON_MOTOR_BOTTOM.get(), pos, state),
                    CIOBlocks.CEE_AIRCON_MOTOR_BOTTOM.get(), CIOBlocks.CEE_AIRCON_MOTOR_BOTTOM_240.get()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirconMotorTopBlockEntity>> AIRCON_MOTOR_TOP =
            BLOCK_ENTITIES.register("aircon_motor_top", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new AirconMotorTopBlockEntity(CIOBlockEntities.AIRCON_MOTOR_TOP.get(), pos, state),
                    CIOBlocks.AIRCON_MOTOR_TOP.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirconVenterBlockEntity>> AIRCON_VENTER =
            BLOCK_ENTITIES.register("aircon_venter", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new AirconVenterBlockEntity(CIOBlockEntities.AIRCON_VENTER.get(), pos, state),
                    CIOBlocks.AIRCON_VENTER.get()).build(null));

    // Iden's Decor Electric buttons & switches — one appliance-grid switch node
    // type behind all ten (see com.cio.createinteroperable.iden). Iden-only.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricSwitchBlockEntity>> ELECTRIC_SWITCH =
            IdenDecorCompat.present() ? BLOCK_ENTITIES.register("electric_switch", () -> BlockEntityType.Builder.of(
                    ElectricSwitchBlockEntity::new,
                    CIOBlocks.ELECTRIC_HEAVY_BUTTON.get(), CIOBlocks.ELECTRIC_GATE_BUTTON.get(),
                    CIOBlocks.ELECTRIC_HEAVY_LEVER.get(), CIOBlocks.ELECTRIC_EMERGENCY_LEVER.get(),
                    CIOBlocks.ELECTRIC_LIGHT_SWITCH.get(), CIOBlocks.ELECTRIC_POWER_SWITCH.get(),
                    CIOBlocks.ELECTRIC_VALVE_SWITCH.get(), CIOBlocks.ELECTRIC_BLAST_LEVER.get(),
                    CIOBlocks.ELECTRIC_CORE_BUTTON_CONTROL_PANEL.get(),
                    CIOBlocks.ELECTRIC_CORE_LEVER_CONTROL_PANEL.get()).build(null)) : null;

    // Iden's Decor telephone — CIO swaps its own block in at Iden's registration
    // (see com.cio.createinteroperable.iden.IdenTelephones); these carry the
    // call logic + appliance-grid node. The PG type backs both the PG-only and
    // the PG+CEE block; the CEE type only exists on a CEE-only install.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IdenPgTelephoneBlockEntity>> IDEN_PG_TELEPHONE =
            IdenDecorCompat.present() && PG ? BLOCK_ENTITIES.register("iden_telephone", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new IdenPgTelephoneBlockEntity(CIOBlockEntities.IDEN_PG_TELEPHONE.get(), pos, state),
                    IdenTelephones.block()).build(null)) : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IdenCeeTelephoneBlockEntity>> IDEN_CEE_TELEPHONE =
            IdenDecorCompat.present() && CEE && !PG ? BLOCK_ENTITIES.register("iden_cee_telephone", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new IdenCeeTelephoneBlockEntity(CIOBlockEntities.IDEN_CEE_TELEPHONE.get(), pos, state),
                    IdenTelephones.block()).build(null)) : null;

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
