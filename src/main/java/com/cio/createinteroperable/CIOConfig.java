package com.cio.createinteroperable;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config for Create: Interoperable.
 *
 * <p>Carries the "water fixtures need a real plumbed supply" integrations:
 * <ul>
 *   <li>{@code refurbishedFurniture} — Crayfish Refurbished Furniture sinks /
 *       baths / toilets, see
 *       {@link com.cio.createinteroperable.mixin.crayfish.RefurbishedFixtureTapMixin};</li>
 *   <li>{@code sink} / {@code bathtub} — the optional Let's Do (Farm &amp; Charm
 *       / Alpine Whispers) integration, see
 *       {@link com.cio.createinteroperable.letsdo.SinkSupplyCompat} (a no-op when
 *       none of those mods are installed).</li>
 * </ul>
 */
public final class CIOConfig {
    public static final ModConfigSpec SPEC;

    /**
     * Master switch, <b>on by default</b>. A Refurbished Furniture kitchen sink,
     * bathroom sink, bath or toilet no longer conjures water for free:
     * right-clicking it bare-handed pulls fluid out of a supply touching the
     * fixture (a tank, a drum, a Create pipe end) into its own tank, and holding
     * right-click keeps pulling. This overrides Refurbished Furniture's own
     * {@code dispenseWater} option entirely. Set false to hand behaviour back to
     * Refurbished Furniture.
     */
    public static final ModConfigSpec.BooleanValue RF_REQUIRE_SUPPLY;

    /** Millibuckets drawn into the fixture per right-click (hold to fill up). */
    public static final ModConfigSpec.IntValue RF_FILL_STEP_MB;

    /**
     * Master switch for the Let's Do integration, <b>on by default</b>. Let's Do
     * sinks and the Alpine Whispers bathtub no longer fill for free: pipes
     * (Create, or any mod exposing a fluid handler on the bottom face) feed an
     * internal buffer, and the player fills the basin by right-clicking the
     * faucet while that buffer holds enough fluid. Alpine Whispers' own sink and
     * bathtub taps run only on piped water instead (see
     * {@code letsdo.AlpineFixture}). When false the sink keeps Farm
     * &amp; Charm's / Alpine Whispers' vanilla behaviour and none of the rest of
     * this section applies. Let's Do is only ever a soft (name-targeted,
     * non-required) integration — a no-op when it isn't installed.
     */
    public static final ModConfigSpec.BooleanValue SINK_REQUIRE_PIPE_SUPPLY;

    /** Simple Voice Chat telephone relay master switch (a no-op without that mod). */
    public static final ModConfigSpec.BooleanValue VOICE_RELAY;

    /** Blocks around a phone in which speech is picked up. */
    public static final ModConfigSpec.DoubleValue VOICE_CAPTURE_RADIUS;

    /** Blocks around the far phone within which the relayed voice is audible. */
    public static final ModConfigSpec.DoubleValue VOICE_PLAYBACK_RANGE;

    /** Loudness of the quiet hiss each phone plays for the length of an answered call; 0 = none. */
    public static final ModConfigSpec.DoubleValue VOICE_LINE_NOISE;

    /** World sounds near a phone on an answered call (jukeboxes, note blocks, machines, mobs...) are heard from the far phone (no other mod needed). */
    public static final ModConfigSpec.BooleanValue MUSIC_RELAY;

    /** Voice and sounds carried down a call lose quality like a compressed phone line. */
    public static final ModConfigSpec.BooleanValue LINE_COMPRESSION;

    /** Radio Telephones may keep their own chunk loaded when a player ticks the option. */
    public static final ModConfigSpec.BooleanValue RADIO_CHUNK_LOADING;

    /** Capacity of a Let's Do sink's internal supply buffer, in millibuckets. */
    public static final ModConfigSpec.IntValue SINK_BUFFER_CAPACITY_MB;

    /**
     * Millibuckets drawn from the buffer each time the player fills a Let's Do
     * basin from the faucet. An Alpine Whispers basin fills in three steps, each
     * costing a third of this.
     */
    public static final ModConfigSpec.IntValue SINK_FILL_COST_MB;

    /**
     * Let a player also top up a Let's Do sink's buffer by hand with a filled
     * bucket (water/lava/milk/…). The buffer still only accepts one fluid at a time.
     */
    public static final ModConfigSpec.BooleanValue SINK_ALLOW_MANUAL_BUCKET_FILL;

    /** Millibuckets of water a full Alpine Whispers bathtub takes, drawn evenly while its tap runs. */
    public static final ModConfigSpec.IntValue BATHTUB_CAPACITY_MB;

    /** Urban Decor sinks, faucets and showers need piped water (a no-op without Urban Decor). */
    public static final ModConfigSpec.BooleanValue UD_REQUIRE_SUPPLY;
    /** Millibuckets per second a running Urban Decor sink or faucet draws. */
    public static final ModConfigSpec.IntValue UD_TAP_MB_PER_SECOND;
    /** Millibuckets per second a running Urban Decor shower draws. */
    public static final ModConfigSpec.IntValue UD_SHOWER_MB_PER_SECOND;
    /** Millibuckets of water an Urban Decor toilet's cistern holds. */
    public static final ModConfigSpec.IntValue UD_TOILET_CAPACITY_MB;
    /** Millibuckets of water a whole (two-half) Urban Decor bathtub holds. */
    public static final ModConfigSpec.IntValue UD_BATHTUB_CAPACITY_MB;
    /** Sitting in a bathtub that is at least 40% full washes harmful effects off. */
    public static final ModConfigSpec.BooleanValue UD_BATH_CLEANSES;
    /** Seconds a washing machine cycle runs. */
    public static final ModConfigSpec.IntValue UD_WASHER_CYCLE_SECONDS;
    /** Millibuckets of water one washing machine cycle uses. */
    public static final ModConfigSpec.IntValue UD_WASHER_WATER_MB;
    /** Percent of an item's full durability a washing machine cycle restores. */
    public static final ModConfigSpec.IntValue UD_WASHER_REPAIR_PERCENT;
    /** Millibuckets a glass bottle takes from an Urban Decor tap. */
    public static final ModConfigSpec.IntValue UD_BOTTLE_COST_MB;
    /** A running shower washes harmful effects off whoever stands under it. */
    public static final ModConfigSpec.BooleanValue UD_SHOWER_CLEANSES;
    /** A running shower also gives whoever stands under it a gentle Regeneration. */
    public static final ModConfigSpec.BooleanValue UD_SHOWER_REGENERATION;

    /**
     * Master switch for the third-party lamp integration, <b>on by default</b>.
     * Let's Do Furniture / Candlelight lamps and street lanterns, Alpine
     * Whispers fairy lights, Another Furniture lamps, and Bibliocraft's Fancy
     * Lamp all become Crayfish electricity consumers: an unwired or unpowered
     * lamp is dark, and it draws 3&nbsp;W (12&nbsp;V) only while lit.
     * Right-click (Another Furniture) or sneak-right-click (Let's Do) toggles
     * the lamp's switch; the Fancy Lamp has no manual toggle (redstone-only,
     * and that redstone response is suppressed once the node is present). Set
     * false to hand every lamp straight back to its own mod (no block entity
     * is attached). A no-op unless one of those mods is installed.
     */
    public static final ModConfigSpec.BooleanValue LETSDO_LAMPS_REQUIRE_POWER;

    /**
     * Master switch for the Let's Do Beachparty appliance integration, <b>on by
     * default</b>. The Radio becomes a Crayfish electricity consumer that only
     * plays while powered (10&nbsp;W, 12&nbsp;V, billed only while a track
     * plays), and the Mini Fridge only ferments while powered (85&nbsp;W,
     * 120&nbsp;V, billed whenever linked). Set false to hand both straight back
     * to Beachparty. A no-op unless Beachparty is installed.
     */
    public static final ModConfigSpec.BooleanValue BEACHPARTY_APPLIANCES_REQUIRE_POWER;

    /**
     * Master switch for the Bibliocraft appliance integration, <b>on by
     * default</b>. The Fancy Crafter's vanilla redstone {@code POWERED} gate
     * (the one its own ticker already refuses to run without) is driven by
     * CIO's electricity grid instead: it only auto-crafts while linked to a
     * live rail (100&nbsp;W, 120&nbsp;V), billed only while it has a recipe
     * staged. Its menu and hopper automation are never blocked, even
     * unpowered &mdash; only the automatic craft tick needs power. Set false
     * to hand it straight back to Bibliocraft's own redstone behaviour. A
     * no-op unless Bibliocraft is installed.
     */
    public static final ModConfigSpec.BooleanValue BIBLIOCRAFT_APPLIANCES_REQUIRE_POWER;

    /**
     * <b>On by default.</b> Caps Bibliocraft's Fancy Lantern (every gold/iron,
     * clear/colored, regular/soul flavor) at a vanilla Soul Lantern's light
     * level (10) at most &mdash; the "regular" flavors otherwise register at a
     * full 15, matching vanilla's own Lantern. A no-op unless Bibliocraft is
     * installed. Set false to restore Bibliocraft's own light levels.
     */
    public static final ModConfigSpec.BooleanValue BIBLIOCRAFT_LANTERN_LIGHT_CAP;

    /**
     * Master switch for the Create Train Navigator display integration, <b>on by
     * default</b>. A freestanding Advanced Display (any of the panel / board /
     * slab / small / sloped shapes) becomes an electricity consumer: it renders
     * its texts only while a live rail from a Domestic Electrical Board reaches
     * it (8&nbsp;W, 12&nbsp;V), and shows a blank screen otherwise. Wiring
     * <em>any</em> block of a multi-block board powers the whole board. Displays
     * assembled onto a moving contraption are never touched. Set false to hand
     * every display straight back to Create Train Navigator (no block entity
     * node attached). A no-op unless Create Train Navigator is installed.
     */
    public static final ModConfigSpec.BooleanValue CRN_DISPLAYS_REQUIRE_POWER;

    /**
     * Master switch for the Vista (cameramod) TV integration, <b>on by
     * default</b>. A Vista TV (single or grown into a connected N&times;N
     * wall) becomes an electricity consumer instead of a plain redstone lamp:
     * with no live rail from a Domestic Electrical Board it is always off, no
     * matter what redstone signal reaches it. Once a rail reaches it, it is on
     * by default with <em>no</em> redstone needed &mdash; redstone is repurposed
     * as a manual kill switch, forcing the screen off without cutting power
     * (billed only while actually on, 8&nbsp;W per tile, 12&nbsp;V,
     * n&sup2; for a grown N&times;N wall). Set false to hand every TV straight
     * back to Vista's own redstone-only behaviour. A no-op unless Vista is
     * installed.
     */
    public static final ModConfigSpec.BooleanValue VISTA_TVS_REQUIRE_POWER;

    /**
     * Master switch for the Iden's Decor Computer integration, <b>on by default</b>.
     * The computer becomes an appliance-grid consumer and shows its inserted
     * floppy disk's text only while powered.
     */
    public static final ModConfigSpec.BooleanValue IDEN_COMPUTER_REQUIRE_POWER;

    /**
     * <b>Purely experimental</b> (experimental branch only; patches Immersive
     * Vehicles internals and is untested in real play). Master switch for the
     * Immersive Vehicles AA Spotlight integration, <b>on by default</b>. A ground-placed MTS Official Content Pack AA Base Plate
     * gets a wireable power terminal on its corner; the Spotlight mounted on it
     * lights its beam, auto-rotates and traverses only while a live rail from a
     * Domestic Electrical Board reaches the plate (6 kW, 120 V while lit).
     */
    public static final ModConfigSpec.BooleanValue MTS_AA_SPOTLIGHT_REQUIRES_POWER;

    /**
     * <b>Purely experimental</b> (see {@link #MTS_AA_SPOTLIGHT_REQUIRES_POWER}).
     * Master switch for the Immersive Vehicles pole-light integration, <b>on by
     * default</b>. Every IV pole block and Signal Controller becomes a wireable
     * grid node; street lights and traffic signals mounted on a pole light only
     * while a live rail reaches that pole.
     */
    public static final ModConfigSpec.BooleanValue MTS_POLE_LIGHTS_REQUIRE_POWER;

    /**
     * Create: Pipes n Physics only. How hard the Aircon Motor pumps, in Create RPM
     * (Pipes n Physics' own pump scale; 256 is the maximum a Mechanical Pump can turn).
     * 0 turns the feature off.
     */
    public static final ModConfigSpec.DoubleValue AIRCON_PUMP_RPM;

    /**
     * Off by default. Whether a temperature mod's ambient temperature (today:
     * Cold Sweat) reaches Power Grid and Electro Energetics devices' own heat
     * model: PG components cool toward it instead of PG's biome formula, and
     * CEE devices warn / burn out sooner in hot spots. Read through
     * {@link com.cio.createinteroperable.thermal.TemperatureMods#affectGridDevices()}.
     */
    public static final ModConfigSpec.BooleanValue TEMPERATURE_MODS_AFFECT_GRID_DEVICES;

    /**
     * On by default. Whether the Steam Hearth (Multi Radiator) and the Aircon
     * parts are offered (creative tab, recipes) when no temperature mod is
     * installed. With one installed they always are. The Steam Outlet is offered
     * either way, and the blocks are registered either way, so placed ones never
     * vanish. Read through
     * {@link com.cio.createinteroperable.thermal.TemperatureMods#climateBlocksEnabled()}.
     */
    public static final ModConfigSpec.BooleanValue CLIMATE_BLOCKS_WITHOUT_TEMPERATURE_MOD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.translation("createinteroperable.configuration.refurbishedFurniture").push("refurbishedFurniture");
        RF_REQUIRE_SUPPLY = b
                .comment("ON by default. Refurbished Furniture sinks / baths / toilets no longer",
                        "fill for free: right-click bare-handed to pull water from an adjacent",
                        "supply (a tank, a drum, a Create pipe end) into the fixture; hold to keep",
                        "filling. This overrides Refurbished Furniture's own dispenseWater option.",
                        "Set false to restore Refurbished Furniture's behaviour.")
                .translation("createinteroperable.configuration.refurbishedFurniture.requireSupply")
                .define("requireSupply", true);
        RF_FILL_STEP_MB = b
                .comment("Millibuckets drawn into the fixture per right-click.")
                .translation("createinteroperable.configuration.refurbishedFurniture.fillStepMb")
                .defineInRange("fillStepMb", 250, 1, 1_000_000);
        b.pop();

        b.translation("createinteroperable.configuration.sink").push("sink");
        SINK_REQUIRE_PIPE_SUPPLY = b
                .comment("ON by default. Let's Do sinks (Farm & Charm / Candlelight / Bakery /",
                        "Alpine Whispers) and the Alpine Whispers bathtub no longer fill for free.",
                        "Farm & Charm-style sinks: pipes (Create, or any mod exposing a fluid handler",
                        "on the bottom face) feed an internal buffer; the player fills the basin by",
                        "right-clicking the faucet when the buffer holds at least fillCostMb.",
                        "Alpine Whispers sink and bathtub: pipes feed the fixture (sink: bottom or",
                        "back; bathtub: any side of the tap end except the top) and the tap only runs",
                        "while that water lasts, closing itself when it runs dry; the bathtub also",
                        "drinks from a tank it touches.",
                        "A no-op unless one of those mods is installed. Set false to restore their",
                        "vanilla behaviour.")
                .translation("createinteroperable.configuration.sink.requirePipeSupply")
                .define("requirePipeSupply", true);
        SINK_BUFFER_CAPACITY_MB = b
                .comment("Capacity of the sink's internal supply buffer, in millibuckets.")
                .translation("createinteroperable.configuration.sink.bufferCapacityMb")
                .defineInRange("bufferCapacityMb", 2000, 1, 1_000_000);
        SINK_FILL_COST_MB = b
                .comment("Millibuckets drawn from the buffer each time the player fills the basin.",
                        "An Alpine Whispers basin fills in three steps, each costing a third of this.")
                .translation("createinteroperable.configuration.sink.fillCostMb")
                .defineInRange("fillCostMb", 1000, 1, 1_000_000);
        SINK_ALLOW_MANUAL_BUCKET_FILL = b
                .comment("Also let players top up the buffer by hand with a filled bucket.")
                .translation("createinteroperable.configuration.sink.allowManualBucketFill")
                .define("allowManualBucketFill", true);
        b.pop();

        b.translation("createinteroperable.configuration.bathtub").push("bathtub");
        BATHTUB_CAPACITY_MB = b
                .comment("Millibuckets of water a full Alpine Whispers bathtub takes, drawn evenly",
                        "while its tap runs.")
                .translation("createinteroperable.configuration.bathtub.capacityMb")
                .defineInRange("capacityMb", 3000, 1, 1_000_000);
        b.pop();

        b.translation("createinteroperable.configuration.urbanDecor").push("urbanDecor");
        UD_REQUIRE_SUPPLY = b
                .comment("ON by default. Urban Decor sinks, faucets and showers no longer run on",
                        "nothing: Create pipes (back of the fixture, plus the bottom of a sink or",
                        "faucet and the top of a shower) feed them, a faucet or shower also drinks",
                        "from a tank it touches, and a tap that runs dry turns itself off.",
                        "A no-op unless Urban Decor is installed.")
                .translation("createinteroperable.configuration.urbanDecor.requireSupply")
                .define("requireSupply", true);
        UD_TAP_MB_PER_SECOND = b
                .comment("Millibuckets per second a running sink or faucet draws.")
                .translation("createinteroperable.configuration.urbanDecor.tapMbPerSecond")
                .defineInRange("tapMbPerSecond", 50, 1, 100_000);
        UD_SHOWER_MB_PER_SECOND = b
                .comment("Millibuckets per second a running shower draws.")
                .translation("createinteroperable.configuration.urbanDecor.showerMbPerSecond")
                .defineInRange("showerMbPerSecond", 100, 1, 100_000);
        UD_TOILET_CAPACITY_MB = b
                .comment("Millibuckets of water a toilet's cistern holds (Refurbished Furniture's holds one",
                        "bucket). Pipes, buckets and bottles fill it; flushing needs it non-empty.")
                .translation("createinteroperable.configuration.urbanDecor.toiletCapacityMb")
                .defineInRange("toiletCapacityMb", 1000, 1, 1_000_000);
        UD_BATHTUB_CAPACITY_MB = b
                .comment("Millibuckets of water a whole bathtub (both halves) holds; Refurbished Furniture's",
                        "bath holds ten buckets. Pipes into either half, buckets and bottles fill it; the tub",
                        "has no tap, so it simply fills while water runs in.")
                .translation("createinteroperable.configuration.urbanDecor.bathtubCapacityMb")
                .defineInRange("bathtubCapacityMb", 10000, 1, 10_000_000);
        UD_BATH_CLEANSES = b
                .comment("Sitting in a bathtub that is at least 40% full washes every harmful effect off.")
                .translation("createinteroperable.configuration.urbanDecor.bathCleanses")
                .define("bathCleanses", true);
        UD_WASHER_CYCLE_SECONDS = b
                .comment("Seconds one washing machine cycle runs (it pauses while unpowered).")
                .translation("createinteroperable.configuration.urbanDecor.washerCycleSeconds")
                .defineInRange("washerCycleSeconds", 30, 1, 3600);
        UD_WASHER_WATER_MB = b
                .comment("Millibuckets of water one washing machine cycle uses, taken when it starts.",
                        "Piped in through any side but the top and the door.")
                .translation("createinteroperable.configuration.urbanDecor.washerWaterMb")
                .defineInRange("washerWaterMb", 500, 1, 100_000);
        UD_WASHER_REPAIR_PERCENT = b
                .comment("Percent of its full durability each damaged item regains from a washing machine",
                        "cycle (every curse on it is removed as well).")
                .translation("createinteroperable.configuration.urbanDecor.washerRepairPercent")
                .defineInRange("washerRepairPercent", 20, 0, 100);
        UD_BOTTLE_COST_MB = b
                .comment("Millibuckets a glass bottle takes from a running tap.")
                .translation("createinteroperable.configuration.urbanDecor.bottleCostMb")
                .defineInRange("bottleCostMb", 250, 1, 1_000_000);
        UD_SHOWER_CLEANSES = b
                .comment("A running shower washes every harmful effect off whoever stands under it.")
                .translation("createinteroperable.configuration.urbanDecor.showerCleanses")
                .define("showerCleanses", true);
        UD_SHOWER_REGENERATION = b
                .comment("A running shower also gives whoever stands under it a gentle Regeneration.")
                .translation("createinteroperable.configuration.urbanDecor.showerRegeneration")
                .define("showerRegeneration", true);
        b.pop();

        b.translation("createinteroperable.configuration.lamps").push("lamps");
        LETSDO_LAMPS_REQUIRE_POWER = b
                .comment("ON by default. Let's Do Furniture / Candlelight lamps and street lanterns,",
                        "Alpine Whispers fairy lights, Another Furniture lamps, and Iden's Decor Wall Lamp /",
                        "Flood Lamp / Floodlight / Fluorescent Light Block become Crayfish",
                        "electricity consumers: unwired or unpowered = dark, and a lit lamp draws",
                        "3 W (12 V). Right-click (Another Furniture) or sneak-right-click (Let's Do)",
                        "toggles a lamp's switch. A no-op unless one of those mods is installed. Set",
                        "false to hand every lamp straight back to its own mod (no block entity",
                        "attached); Another Furniture lamps also regain their redstone response.",
                        "Also makes new Iden's Decor lights (Pliers-configurable) place in their",
                        "Refurbished mode when Refurbished Furniture is installed.")
                .translation("createinteroperable.configuration.lamps.requirePower")
                .define("requirePower", true);
        b.pop();

        b.translation("createinteroperable.configuration.beachpartyAppliances").push("beachpartyAppliances");
        BEACHPARTY_APPLIANCES_REQUIRE_POWER = b
                .comment("ON by default. Let's Do Beachparty's Radio and Mini Fridge become",
                        "Crayfish electricity consumers: the Radio only plays while powered (10 W,",
                        "12 V, billed only while a track plays) and the Mini Fridge only ferments",
                        "while powered (85 W, 120 V, billed whenever linked). A no-op unless",
                        "Beachparty is installed. Set false to restore their vanilla behaviour.")
                .translation("createinteroperable.configuration.beachpartyAppliances.requirePower")
                .define("requirePower", true);
        b.pop();

        b.translation("createinteroperable.configuration.bibliocraftAppliances").push("bibliocraftAppliances");
        BIBLIOCRAFT_APPLIANCES_REQUIRE_POWER = b
                .comment("ON by default. Bibliocraft's Fancy Crafter no longer auto-crafts from",
                        "redstone alone: it needs a live rail from a Domestic Electrical Board",
                        "(100 W, 120 V), billed only while it has a recipe staged. The menu and",
                        "hopper automation are never blocked, even unpowered. A no-op unless",
                        "Bibliocraft is installed. Set false to hand it straight back to",
                        "Bibliocraft's own redstone behaviour.")
                .translation("createinteroperable.configuration.bibliocraftAppliances.requirePower")
                .define("requirePower", true);
        b.pop();

        b.translation("createinteroperable.configuration.bibliocraftLanterns").push("bibliocraftLanterns");
        BIBLIOCRAFT_LANTERN_LIGHT_CAP = b
                .comment("ON by default. Caps Bibliocraft's Fancy Lantern (every gold/iron,",
                        "clear/colored, regular/soul flavor) at a vanilla Soul Lantern's light",
                        "level (10) at most — the 'regular' flavors otherwise register at a full",
                        "15, matching vanilla's own Lantern. A no-op unless Bibliocraft is",
                        "installed. Set false to restore Bibliocraft's own light levels.")
                .translation("createinteroperable.configuration.bibliocraftLanterns.capLight")
                .define("capLight", true);
        b.pop();

        b.translation("createinteroperable.configuration.crnDisplays").push("crnDisplays");
        CRN_DISPLAYS_REQUIRE_POWER = b
                .comment("ON by default. A freestanding Create Train Navigator Advanced Display",
                        "renders its texts only while a live rail from a Domestic Electrical Board",
                        "reaches it (8 W, 12 V); unwired or unpowered = blank screen. Wiring any",
                        "one block of a multi-block board powers the whole board. Super-Glued",
                        "displays and displays built onto a moving contraption or train need no",
                        "power. A no-op unless Create Train",
                        "Navigator is installed. Set false to hand displays back to Create Train",
                        "Navigator (no node attached).")
                .translation("createinteroperable.configuration.crnDisplays.requirePower")
                .define("requirePower", true);
        b.pop();

        b.translation("createinteroperable.configuration.vistaTvs").push("vistaTvs");
        VISTA_TVS_REQUIRE_POWER = b
                .comment("ON by default. A Vista (cameramod) TV — single or grown into a",
                        "connected N x N wall — becomes a Domestic Electrical Board electricity",
                        "consumer instead of a plain redstone lamp: no live rail = always off, no",
                        "matter what redstone signal reaches it. Once a rail reaches it, it is on",
                        "by default with NO redstone needed; redstone is repurposed as a manual",
                        "kill switch that forces the screen off without cutting power. Billed only",
                        "while actually on (8 W per tile, 12 V, n^2 for a grown wall). A no-op",
                        "unless Vista is installed. Set false to hand every TV straight back to",
                        "Vista's own redstone-only behaviour.")
                .translation("createinteroperable.configuration.vistaTvs.requirePower")
                .define("requirePower", true);
        b.pop();

        b.translation("createinteroperable.configuration.idenComputer").push("idenComputer");
        IDEN_COMPUTER_REQUIRE_POWER = b
                .comment("ON by default. Iden's Decor's Computer becomes a Domestic Electrical",
                        "Board electricity consumer: it only shows an inserted floppy disk's text",
                        "while a live rail reaches it (15 W, 12 V, billed only while a disk is in).",
                        "Disks still insert and eject unpowered. A no-op unless Iden's Decor is",
                        "installed. Set false to hand the computer straight back to Iden's Decor.")
                .translation("createinteroperable.configuration.idenComputer.requirePower")
                .define("requirePower", true);
        b.pop();

        b.translation("createinteroperable.configuration.mtsAaSpotlight").push("mtsAaSpotlight");
        MTS_AA_SPOTLIGHT_REQUIRES_POWER = b
                .comment("PURELY EXPERIMENTAL: patches Immersive Vehicles internals, untested in real",
                        "play, and may break on any Immersive Vehicles update.",
                        "ON by default. An Immersive Vehicles (MTS Official Content Pack) AA Base",
                        "Plate placed on the ground gets a power terminal on one corner, wireable to",
                        "a Domestic Electrical Board like any appliance. The AA Spotlight mounted on",
                        "it only lights its beam, auto-rotates or traverses while a live rail reaches",
                        "the plate: 6 kW on the 120 V rail while lit (+5% while auto-rotating), so",
                        "it needs a heavy supply. Unpowered, it stays dark and still and shows",
                        "\"Missing power\". Spotlights on vehicles, and the AA guns, are untouched. A",
                        "no-op unless Immersive Vehicles is installed. Set false to hand the",
                        "Spotlight straight back to Immersive Vehicles (no power needed).")
                .translation("createinteroperable.configuration.mtsAaSpotlight.requirePower")
                .define("requirePower", true);
        b.pop();

        b.translation("createinteroperable.configuration.mtsPoleLights").push("mtsPoleLights");
        MTS_POLE_LIGHTS_REQUIRE_POWER = b
                .comment("PURELY EXPERIMENTAL: patches Immersive Vehicles internals, untested in real",
                        "play, and may break on any Immersive Vehicles update.",
                        "ON by default. Every Immersive Vehicles pole block and Signal Controller",
                        "becomes a wireable Domestic Electrical Board node (a bare pole is a free wire",
                        "junction; the Signal Controller draws nothing for now). Street lights,",
                        "flashing signals, traffic and crossing signals mounted on a pole only light",
                        "while a live rail reaches that pole block: unpowered, every lamp on them is",
                        "dark and street lights stop lighting the world. 100 W per street-light-type",
                        "component, 25 W per traffic-signal-type component, on the 120 V rail. Signs",
                        "are untouched. A no-op unless Immersive Vehicles is installed. Set false to",
                        "hand every pole light straight back to Immersive Vehicles (no power needed).")
                .translation("createinteroperable.configuration.mtsPoleLights.requirePower")
                .define("requirePower", true);
        b.pop();

        b.translation("createinteroperable.configuration.aircon").push("aircon");
        AIRCON_PUMP_RPM = b
                .comment("A no-op unless Create: Pipes n Physics is installed. With it, the Aircon",
                        "Motor acts as its own pump: the cold-air face pushes cold air out and the",
                        "hot-air face sucks hot air in, so an AC loop needs no pumps along its pipes.",
                        "This is the pump strength at full power, on Pipes n Physics' Create-RPM",
                        "scale (256 = the fastest a Mechanical Pump turns). It scales down with the",
                        "motor's power. 0 disables it and leaves the motor as a plain tank.")
                .translation("createinteroperable.configuration.aircon.motorPumpRpm")
                .defineInRange("motorPumpRpm", 256.0, 0.0, 256.0);
        b.pop();

        b.translation("createinteroperable.configuration.temperatureMods").push("temperatureMods");
        TEMPERATURE_MODS_AFFECT_GRID_DEVICES = b
                .comment("Temperature Mods should affect Power Grid and Electro Energetics devices.",
                        "OFF by default. When on, and a temperature mod (Cold Sweat) is installed, the",
                        "temperature around a Power Grid or Electro Energetics device is the temperature",
                        "mod's: devices in hot places overheat sooner and devices in cold places have",
                        "more headroom (Power Grid's Thermometer shows the same). When off, both mods",
                        "use their own heat models as if no temperature mod were installed. Cold",
                        "Sweat's effects on players (heaters, radiators, aircon) are not affected.")
                .translation("createinteroperable.configuration.temperatureMods.affectGridDevices")
                .define("affectGridDevices", false);
        CLIMATE_BLOCKS_WITHOUT_TEMPERATURE_MOD = b
                .comment("Enable Steam Hearth and Air Conditioning even without Cold Sweat.",
                        "ON by default. When no temperature mod (Cold Sweat) is installed, the Steam",
                        "Hearth (Multi Radiator) and the Aircon parts stay craftable and in the",
                        "creative tab; they work as steam and air machines but change no temperature.",
                        "Set false to withhold them unless a temperature mod is installed. The Steam",
                        "Outlet is always available. Blocks already placed keep working either way.",
                        "Takes effect when a world is next loaded.")
                .translation("createinteroperable.configuration.temperatureMods.climateBlocksWithoutTemperatureMod")
                .worldRestart()
                .define("climateBlocksWithoutTemperatureMod", true);
        b.pop();

        b.translation("createinteroperable.configuration.telephoneVoice").push("telephoneVoice");
        VOICE_RELAY = b
                .comment("A no-op unless Simple Voice Chat is installed. While a telephone call is",
                        "answered, players speaking near either phone are heard from the other one.",
                        "Set false to turn the relay off.")
                .translation("createinteroperable.configuration.telephoneVoice.relayEnabled")
                .define("relayEnabled", true);
        VOICE_CAPTURE_RADIUS = b
                .comment("Blocks around a phone in which a player's voice is picked up and carried",
                        "down the line. Whispering picks up half this distance.")
                .translation("createinteroperable.configuration.telephoneVoice.captureRadius")
                .defineInRange("captureRadius", 5.0, 1.0, 32.0);
        VOICE_PLAYBACK_RANGE = b
                .comment("Blocks around the far phone within which the carried voice can be heard. A phone",
                        "is a small speaker: 8 at most (other sounds from a phone always carry 8 blocks).")
                .translation("createinteroperable.configuration.telephoneVoice.playbackRange")
                .defineInRange("playbackRange", 8.0, 1.0, 8.0);
        VOICE_LINE_NOISE = b
                .comment("A faint hiss with the odd soft crackle, played from both phones for as long as",
                        "a call is answered - mild by default. 0 turns it off, 1 is roughly four times",
                        "as loud as the default.")
                .translation("createinteroperable.configuration.telephoneVoice.lineNoise")
                .defineInRange("lineNoise", 0.25, 0.0, 1.0);
        MUSIC_RELAY = b
                .comment("While a telephone call is answered, the sounds within 16 blocks of either phone",
                        "(jukeboxes, note blocks, machines, bells, engines, mobs, footsteps...) are heard",
                        "from the other one, as loud as they are at the phone. Jukebox songs join where the",
                        "jukebox is in the song, and stop with it. Needs no other mod.")
                .translation("createinteroperable.configuration.telephoneVoice.relayMusic")
                .define("relayMusic", true);
        LINE_COMPRESSION = b
                .comment("Voice and sounds carried down a call come out of the far phone band-limited and",
                        "coarsened, like audio through a low-bitrate phone line. False plays them as they are.")
                .translation("createinteroperable.configuration.telephoneVoice.compressAudio")
                .define("compressAudio", true);
        b.pop();

        b.translation("createinteroperable.configuration.radioTelephone").push("radioTelephone");
        RADIO_CHUNK_LOADING = b
                .comment("Lets players tick \"Keep chunk loaded\" in a Radio Telephone's settings: the phone",
                        "then keeps its own chunk loaded and ticking, so it rings and answers with nobody",
                        "nearby. False: the option does nothing, and chunks phones held are let go when",
                        "the world loads.")
                .translation("createinteroperable.configuration.radioTelephone.allowChunkLoading")
                .define("allowChunkLoading", true);
        b.pop();

        SPEC = b.build();
    }

    private CIOConfig() {
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
