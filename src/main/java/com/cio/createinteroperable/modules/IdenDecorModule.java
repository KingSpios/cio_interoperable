package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.CIOCreativeTab;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.iden.ElectricButtonBlock;
import com.cio.createinteroperable.iden.ElectricControlPanelBlock;
import com.cio.createinteroperable.iden.ElectricHeavyLeverBlock;
import com.cio.createinteroperable.iden.ElectricLeverBlock;
import com.cio.createinteroperable.iden.ElectricSwitchBlockEntity;
import com.cio.createinteroperable.iden.ElectricSwitches;
import com.cio.createinteroperable.iden.IdenCeeTelephoneBlockEntity;
import com.cio.createinteroperable.iden.IdenPgTelephoneBlockEntity;
import com.cio.createinteroperable.iden.IdenTelephones;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Iden's Decor content: the ten "Electric" buttons & switches (appliance-grid
 * switch twins of Iden's redstone ones, rendered with Iden's own models), and
 * the block entity behind CIO's swapped-in Iden telephone — whose variant
 * depends on the grid mods, so it lives in the nested {@link PgTelephone} /
 * {@link CeeTelephone} modules. Touched only after {@link CIOModules} has
 * checked {@link #REQUIRES}.
 */
public final class IdenDecorModule {
    public static final String REQUIRES = Mods.Id.IDEN_DECOR;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInteroperable.ID);

    private IdenDecorModule() {
    }

    // --- Blocks ---

    public static final DeferredBlock<ElectricButtonBlock> ELECTRIC_HEAVY_BUTTON = BLOCKS.register("electric_heavy_button",
            () -> new ElectricButtonBlock(ElectricButtonBlock.Style.HEAVY, idenButtonProperties()));

    public static final DeferredBlock<ElectricButtonBlock> ELECTRIC_GATE_BUTTON = BLOCKS.register("electric_gate_button",
            () -> new ElectricButtonBlock(ElectricButtonBlock.Style.GATE, idenButtonProperties()));

    public static final DeferredBlock<ElectricHeavyLeverBlock> ELECTRIC_HEAVY_LEVER = BLOCKS.register("electric_heavy_lever",
            () -> new ElectricHeavyLeverBlock(idenLeverProperties()));

    public static final DeferredBlock<ElectricLeverBlock> ELECTRIC_EMERGENCY_LEVER = BLOCKS.register("electric_emergency_lever",
            () -> new ElectricLeverBlock(idenLeverProperties()));

    public static final DeferredBlock<ElectricLeverBlock> ELECTRIC_LIGHT_SWITCH = BLOCKS.register("electric_light_switch",
            () -> new ElectricLeverBlock(idenLeverProperties()));

    public static final DeferredBlock<ElectricLeverBlock> ELECTRIC_POWER_SWITCH = BLOCKS.register("electric_power_switch",
            () -> new ElectricLeverBlock(idenLeverProperties()));

    public static final DeferredBlock<ElectricLeverBlock> ELECTRIC_VALVE_SWITCH = BLOCKS.register("electric_valve_switch",
            () -> new ElectricLeverBlock(idenLeverProperties()));

    // Iden's Blast Lever is solid (no noCollission) and tougher.
    public static final DeferredBlock<ElectricLeverBlock> ELECTRIC_BLAST_LEVER = BLOCKS.register("electric_blast_lever",
            () -> new ElectricLeverBlock(BlockBehaviour.Properties.of().strength(2.0f)));

    public static final DeferredBlock<ElectricControlPanelBlock> ELECTRIC_CORE_BUTTON_CONTROL_PANEL = BLOCKS.register("electric_core_button_control_panel",
            () -> new ElectricControlPanelBlock(true, idenPanelProperties()));

    public static final DeferredBlock<ElectricControlPanelBlock> ELECTRIC_CORE_LEVER_CONTROL_PANEL = BLOCKS.register("electric_core_lever_control_panel",
            () -> new ElectricControlPanelBlock(false, idenPanelProperties()));

    // --- Block entities ---

    // Iden's Decor Electric buttons & switches — one appliance-grid switch node
    // type behind all ten (see com.cio.createinteroperable.iden). Iden-only.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricSwitchBlockEntity>> ELECTRIC_SWITCH_BE =
            BLOCK_ENTITIES.register("electric_switch", () -> BlockEntityType.Builder.of(
                    ElectricSwitchBlockEntity::new,
                    IdenDecorModule.ELECTRIC_HEAVY_BUTTON.get(), IdenDecorModule.ELECTRIC_GATE_BUTTON.get(),
                    IdenDecorModule.ELECTRIC_HEAVY_LEVER.get(), IdenDecorModule.ELECTRIC_EMERGENCY_LEVER.get(),
                    IdenDecorModule.ELECTRIC_LIGHT_SWITCH.get(), IdenDecorModule.ELECTRIC_POWER_SWITCH.get(),
                    IdenDecorModule.ELECTRIC_VALVE_SWITCH.get(), IdenDecorModule.ELECTRIC_BLAST_LEVER.get(),
                    IdenDecorModule.ELECTRIC_CORE_BUTTON_CONTROL_PANEL.get(),
                    IdenDecorModule.ELECTRIC_CORE_LEVER_CONTROL_PANEL.get()).build(null));

    // --- Items ---

    public static final DeferredItem<BlockItem> ELECTRIC_HEAVY_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_heavy_button", IdenDecorModule.ELECTRIC_HEAVY_BUTTON);

    public static final DeferredItem<BlockItem> ELECTRIC_GATE_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_gate_button", IdenDecorModule.ELECTRIC_GATE_BUTTON);

    public static final DeferredItem<BlockItem> ELECTRIC_HEAVY_LEVER_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_heavy_lever", IdenDecorModule.ELECTRIC_HEAVY_LEVER);

    public static final DeferredItem<BlockItem> ELECTRIC_EMERGENCY_LEVER_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_emergency_lever", IdenDecorModule.ELECTRIC_EMERGENCY_LEVER);

    public static final DeferredItem<BlockItem> ELECTRIC_LIGHT_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_light_switch", IdenDecorModule.ELECTRIC_LIGHT_SWITCH);

    public static final DeferredItem<BlockItem> ELECTRIC_POWER_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_power_switch", IdenDecorModule.ELECTRIC_POWER_SWITCH);

    public static final DeferredItem<BlockItem> ELECTRIC_VALVE_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_valve_switch", IdenDecorModule.ELECTRIC_VALVE_SWITCH);

    public static final DeferredItem<BlockItem> ELECTRIC_BLAST_LEVER_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_blast_lever", IdenDecorModule.ELECTRIC_BLAST_LEVER);

    public static final DeferredItem<BlockItem> ELECTRIC_CORE_BUTTON_CONTROL_PANEL_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_core_button_control_panel", IdenDecorModule.ELECTRIC_CORE_BUTTON_CONTROL_PANEL);

    public static final DeferredItem<BlockItem> ELECTRIC_CORE_LEVER_CONTROL_PANEL_ITEM = ITEMS.registerSimpleBlockItem(
            "electric_core_lever_control_panel", IdenDecorModule.ELECTRIC_CORE_LEVER_CONTROL_PANEL);

    // Properties copied from Iden's ModBlocks.
    private static BlockBehaviour.Properties idenButtonProperties() {
        return BlockBehaviour.Properties.of().pushReaction(PushReaction.DESTROY).noCollission().strength(0.5f).noOcclusion();
    }

    private static BlockBehaviour.Properties idenLeverProperties() {
        return BlockBehaviour.Properties.of().strength(0.5f).noCollission();
    }

    private static BlockBehaviour.Properties idenPanelProperties() {
        return BlockBehaviour.Properties.of().sound(SoundType.METAL).noOcclusion().strength(3.5f, 4.0f);
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        // Rename Iden's redstone buttons/switches "Redstone ..." so they read
        // apart from CIO's Electric twins (see ElectricSwitches).
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> ElectricSwitches.renameOriginals());

        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_HEAVY_BUTTON_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_GATE_BUTTON_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_HEAVY_LEVER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_EMERGENCY_LEVER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_LIGHT_SWITCH_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_POWER_SWITCH_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_VALVE_SWITCH_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_BLAST_LEVER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_CORE_BUTTON_CONTROL_PANEL_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.SWITCHES, ELECTRIC_CORE_LEVER_CONTROL_PANEL_ITEM);

        // The PG type backs both the PG-only and the PG+CEE telephone block;
        // the CEE type only exists on a CEE-only install.
        if (CIOModules.isEnabled(PgTelephone.REQUIRES)) {
            PgTelephone.register(modEventBus);
            CIOModules.markEnabled("IdenDecorModule.PgTelephone");
        } else if (CIOModules.isEnabled(CeeTelephone.REQUIRES)) {
            CeeTelephone.register(modEventBus);
            CIOModules.markEnabled("IdenDecorModule.CeeTelephone");
        }
    }

    /** The Iden telephone's block entity on an install with Power Grid. */
    public static final class PgTelephone {
        public static final String REQUIRES = IdenDecorModule.REQUIRES + "," + Mods.Id.POWER_GRID;

        private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);

        // Iden's Decor telephone — CIO swaps its own block in at Iden's registration
        // (see com.cio.createinteroperable.iden.IdenTelephones); these carry the
        // call logic + appliance-grid node. The PG type backs both the PG-only and
        // the PG+CEE block; the CEE type only exists on a CEE-only install.
        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IdenPgTelephoneBlockEntity>> BLOCK_ENTITY =
                BLOCK_ENTITIES.register("iden_telephone", () -> BlockEntityType.Builder.of(
                        (pos, state) -> new IdenPgTelephoneBlockEntity(IdenDecorModule.PgTelephone.BLOCK_ENTITY.get(), pos, state),
                        IdenTelephones.block()).build(null));

        private PgTelephone() {
        }

        static void register(IEventBus modEventBus) {
            BLOCK_ENTITIES.register(modEventBus);
            if (FMLEnvironment.dist.isClient()) {
                IdenDecorModuleClient.registerTelephone(modEventBus, BLOCK_ENTITY);
            }
        }
    }

    /** The Iden telephone's block entity on a CEE-only install (no Power Grid). */
    public static final class CeeTelephone {
        public static final String REQUIRES = IdenDecorModule.REQUIRES + "," + Mods.Id.ELECTRO_ENERGETICS;

        private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IdenCeeTelephoneBlockEntity>> BLOCK_ENTITY =
                BLOCK_ENTITIES.register("iden_cee_telephone", () -> BlockEntityType.Builder.of(
                        (pos, state) -> new IdenCeeTelephoneBlockEntity(IdenDecorModule.CeeTelephone.BLOCK_ENTITY.get(), pos, state),
                        IdenTelephones.block()).build(null));

        private CeeTelephone() {
        }

        static void register(IEventBus modEventBus) {
            BLOCK_ENTITIES.register(modEventBus);
            if (FMLEnvironment.dist.isClient()) {
                IdenDecorModuleClient.registerTelephone(modEventBus, BLOCK_ENTITY);
            }
        }
    }
}
