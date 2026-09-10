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
     * faucet while that buffer holds enough fluid. When false the sink keeps Farm
     * &amp; Charm's / Alpine Whispers' vanilla behaviour and none of the rest of
     * this section applies. Let's Do is only ever a soft (name-targeted,
     * non-required) integration — a no-op when it isn't installed.
     */
    public static final ModConfigSpec.BooleanValue SINK_REQUIRE_PIPE_SUPPLY;

    /** Capacity of a Let's Do sink's internal supply buffer, in millibuckets. */
    public static final ModConfigSpec.IntValue SINK_BUFFER_CAPACITY_MB;

    /** Millibuckets drawn from the buffer each time the player fills a Let's Do basin from the faucet. */
    public static final ModConfigSpec.IntValue SINK_FILL_COST_MB;

    /**
     * Let a player also top up a Let's Do sink's buffer by hand with a filled
     * bucket (water/lava/milk/…). The buffer still only accepts one fluid at a time.
     */
    public static final ModConfigSpec.BooleanValue SINK_ALLOW_MANUAL_BUCKET_FILL;

    /** Millibuckets a full Alpine Whispers bathtub represents (governs how long it takes to fill). */
    public static final ModConfigSpec.IntValue BATHTUB_CAPACITY_MB;

    /** Millibuckets pulled from an adjacent supply per right-click while filling a bathtub (hold to keep filling). */
    public static final ModConfigSpec.IntValue BATHTUB_FILL_STEP_MB;

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

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("refurbishedFurniture");
        RF_REQUIRE_SUPPLY = b
                .comment("ON by default. Refurbished Furniture sinks / baths / toilets no longer",
                        "fill for free: right-click bare-handed to pull water from an adjacent",
                        "supply (a tank, a drum, a Create pipe end) into the fixture; hold to keep",
                        "filling. This overrides Refurbished Furniture's own dispenseWater option.",
                        "Set false to restore Refurbished Furniture's behaviour.")
                .define("requireSupply", true);
        RF_FILL_STEP_MB = b
                .comment("Millibuckets drawn into the fixture per right-click.")
                .defineInRange("fillStepMb", 250, 1, 1_000_000);
        b.pop();

        b.push("sink");
        SINK_REQUIRE_PIPE_SUPPLY = b
                .comment("ON by default. Let's Do (Farm & Charm / Candlelight / Bakery) sinks and",
                        "the Alpine Whispers bathtub no longer fill for free:",
                        "Sinks: pipes (Create, or any mod exposing a fluid handler on the bottom",
                        "face) feed an internal buffer; the player fills the basin by right-clicking",
                        "the faucet when the buffer holds at least fillCostMb.",
                        "Bathtub: right-click (hold) draws water from an adjacent supply until full.",
                        "A no-op unless one of those mods is installed. Set false to restore their",
                        "vanilla behaviour.")
                .define("requirePipeSupply", true);
        SINK_BUFFER_CAPACITY_MB = b
                .comment("Capacity of the sink's internal supply buffer, in millibuckets.")
                .defineInRange("bufferCapacityMb", 2000, 1, 1_000_000);
        SINK_FILL_COST_MB = b
                .comment("Millibuckets drawn from the buffer each time the player fills the basin.")
                .defineInRange("fillCostMb", 1000, 1, 1_000_000);
        SINK_ALLOW_MANUAL_BUCKET_FILL = b
                .comment("Also let players top up the buffer by hand with a filled bucket.")
                .define("allowManualBucketFill", true);
        b.pop();

        b.push("bathtub");
        BATHTUB_CAPACITY_MB = b
                .comment("Millibuckets a full Alpine Whispers bathtub holds. Larger = slower to fill.")
                .defineInRange("capacityMb", 3000, 1, 1_000_000);
        BATHTUB_FILL_STEP_MB = b
                .comment("Millibuckets pulled from an adjacent supply per right-click while filling",
                        "the bathtub. Hold right-click to keep filling.")
                .defineInRange("fillStepMb", 100, 1, 1_000_000);
        b.pop();

        b.push("lamps");
        LETSDO_LAMPS_REQUIRE_POWER = b
                .comment("ON by default. Let's Do Furniture / Candlelight lamps and street lanterns,",
                        "Alpine Whispers fairy lights, and Another Furniture lamps become Crayfish",
                        "electricity consumers: unwired or unpowered = dark, and a lit lamp draws",
                        "3 W (12 V). Right-click (Another Furniture) or sneak-right-click (Let's Do)",
                        "toggles a lamp's switch. A no-op unless one of those mods is installed. Set",
                        "false to hand every lamp straight back to its own mod (no block entity",
                        "attached); Another Furniture lamps also regain their redstone response.")
                .define("requirePower", true);
        b.pop();

        b.push("beachpartyAppliances");
        BEACHPARTY_APPLIANCES_REQUIRE_POWER = b
                .comment("ON by default. Let's Do Beachparty's Radio and Mini Fridge become",
                        "Crayfish electricity consumers: the Radio only plays while powered (10 W,",
                        "12 V, billed only while a track plays) and the Mini Fridge only ferments",
                        "while powered (85 W, 120 V, billed whenever linked). A no-op unless",
                        "Beachparty is installed. Set false to restore their vanilla behaviour.")
                .define("requirePower", true);
        b.pop();

        b.push("bibliocraftAppliances");
        BIBLIOCRAFT_APPLIANCES_REQUIRE_POWER = b
                .comment("ON by default. Bibliocraft's Fancy Crafter no longer auto-crafts from",
                        "redstone alone: it needs a live rail from a Domestic Electrical Board",
                        "(100 W, 120 V), billed only while it has a recipe staged. The menu and",
                        "hopper automation are never blocked, even unpowered. A no-op unless",
                        "Bibliocraft is installed. Set false to hand it straight back to",
                        "Bibliocraft's own redstone behaviour.")
                .define("requirePower", true);
        b.pop();

        b.push("bibliocraftLanterns");
        BIBLIOCRAFT_LANTERN_LIGHT_CAP = b
                .comment("ON by default. Caps Bibliocraft's Fancy Lantern (every gold/iron,",
                        "clear/colored, regular/soul flavor) at a vanilla Soul Lantern's light",
                        "level (10) at most — the 'regular' flavors otherwise register at a full",
                        "15, matching vanilla's own Lantern. A no-op unless Bibliocraft is",
                        "installed. Set false to restore Bibliocraft's own light levels.")
                .define("capLight", true);
        b.pop();

        b.push("crnDisplays");
        CRN_DISPLAYS_REQUIRE_POWER = b
                .comment("ON by default. A freestanding Create Train Navigator Advanced Display",
                        "renders its texts only while a live rail from a Domestic Electrical Board",
                        "reaches it (8 W, 12 V); unwired or unpowered = blank screen. Wiring any",
                        "one block of a multi-block board powers the whole board. Displays built",
                        "onto a moving contraption are never touched. A no-op unless Create Train",
                        "Navigator is installed. Set false to hand displays back to Create Train",
                        "Navigator (no node attached).")
                .define("requirePower", true);
        b.pop();

        SPEC = b.build();
    }

    private CIOConfig() {
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
