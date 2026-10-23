package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.thermal.coldsweat.ColdSweatIntegration;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.compat.PipesNPhysicsIntegration;
import com.cio.createinteroperable.voice.VoiceCallTracker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place CIO decides which optional-mod content exists.
 *
 * <p>Every gated feature is a <em>module</em>: a class declaring
 * {@code public static final String REQUIRES} (the mod ids it needs, comma
 * separated) and a static {@code register} method. Because {@code REQUIRES} is
 * a compile-time constant, javac inlines it here — checking it never loads the
 * module class, so nothing a module touches is loaded unless every mod it
 * names is installed. A module owns its own registers, creative-tab entries
 * (in the single {@link com.cio.createinteroperable.CIOCreativeTab}) and
 * client hooks.</p>
 *
 * <p>{@code LoadSafetyTest} reads {@code REQUIRES} from the bytecode and fails
 * the build if a module touches a mod it doesn't name; {@code smokeTest} boots
 * real dedicated servers on reduced mod sets and checks the "modules enabled"
 * line this logs against what each mod set should enable.</p>
 *
 * <p>Order matters: registration order is creative-tab order inside a
 * section (Power Grid before Electro Energetics, as before the split).</p>
 */
public final class CIOModules {

    /** Log line prefix the smoke test parses; keep in sync with SmokeRunner. */
    static final String ENABLED_LOG_PREFIX = "Create: Interoperable modules enabled: ";

    private static final List<String> ENABLED = new ArrayList<>();

    private CIOModules() {
    }

    public static void register(IEventBus modEventBus) {
        if (isEnabled(PowerGridModule.REQUIRES)) {
            PowerGridModule.register(modEventBus);
            markEnabled("PowerGridModule");
        }
        if (isEnabled(ElectroEnergeticsModule.REQUIRES)) {
            ElectroEnergeticsModule.register(modEventBus);
            markEnabled("ElectroEnergeticsModule");
        }
        // EXPERIMENTAL (experimental branch only): the CPG / CEE Redstone Switch.
        // Off the creative tab by design.
        if (isEnabled(RedstoneSwitchModule.Cpg.REQUIRES)) {
            RedstoneSwitchModule.Cpg.register(modEventBus);
            markEnabled("RedstoneSwitchModule.Cpg");
        }
        if (isEnabled(RedstoneSwitchModule.Cee.REQUIRES)) {
            RedstoneSwitchModule.Cee.register(modEventBus);
            markEnabled("RedstoneSwitchModule.Cee");
        }
        if (isEnabled(BridgeModule.REQUIRES)) {
            BridgeModule.register(modEventBus);
            markEnabled("BridgeModule");
        }
        if (isEnabled(IdenDecorModule.REQUIRES)) {
            markEnabled("IdenDecorModule");
            IdenDecorModule.register(modEventBus);
        }
        if (isEnabled(RefurbishedFurnitureModule.REQUIRES)) {
            RefurbishedFurnitureModule.register(modEventBus);
            markEnabled("RefurbishedFurnitureModule");
        }
        if (isEnabled(ImmersiveVehiclesModule.REQUIRES)) {
            ImmersiveVehiclesModule.register(modEventBus);
            markEnabled("ImmersiveVehiclesModule");
        }
        if (isEnabled(PantographsAndWiresModule.REQUIRES)) {
            PantographsAndWiresModule.register(modEventBus);
            markEnabled("PantographsAndWiresModule");
        }
        // See ColdSweatIntegration's own doc for why this is a runtime-gated
        // call rather than @EventBusSubscriber: that annotation would let
        // NeoForge's classpath scanner load the class (and touch Cold-Sweat-only
        // types in its method signatures) unconditionally.
        if (isEnabled(ColdSweatIntegration.REQUIRES)) {
            ColdSweatIntegration.register();
            markEnabled("ColdSweatIntegration");
        }
        if (isEnabled(PipesNPhysicsIntegration.REQUIRES)) {
            PipesNPhysicsIntegration.register(modEventBus);
            markEnabled("PipesNPhysicsIntegration");
        }
        // Voice relay for answered Telephone calls. VoiceCallTracker holds no Simple Voice Chat
        // types; the plugin class itself is found and instantiated by Simple Voice Chat.
        if (isEnabled(VoiceCallTracker.REQUIRES)) {
            NeoForge.EVENT_BUS.register(VoiceCallTracker.class);
            markEnabled("VoiceCallTracker");
        }
        CreateInteroperable.LOGGER.info(ENABLED_LOG_PREFIX + (ENABLED.isEmpty() ? "none" : String.join(", ", ENABLED)));
    }

    /** Records a module (or a module's nested sub-module) for the startup log line. */
    static void markEnabled(String module) {
        ENABLED.add(module);
    }

    /**
     * True when every mod id in {@code requires} (comma separated) is loaded
     * (see {@link Mods}).
     * Pass a module's {@code REQUIRES} constant, never a module method: that
     * keeps the check from loading the module. Safe from mod construction on.
     */
    public static boolean isEnabled(String requires) {
        for (String id : requires.split(",")) {
            if (!Mods.isLoaded(id.trim())) {
                return false;
            }
        }
        return true;
    }

}
