package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Chooses which CIO class replaces Iden's Decor's telephone at Iden's own
 * registration ({@code mixin.letsdo.IdenModBlocksMixin}): Power Grid +
 * Electro Energetics &rarr; {@link IdenInteropTelephoneBlock} (CPG and CEE
 * taps), Power Grid only &rarr; {@link IdenPgTelephoneBlock}, Electro
 * Energetics only &rarr; {@link IdenCeeTelephoneBlock}; neither &rarr; Iden's
 * own block is left alone.
 *
 * <p>Each supplier is typed to its exact class (never widened to
 * {@code Supplier<Block>} inside a lambda), so nothing here makes the verifier
 * load a Power Grid or Electro Energetics class on an install without it.</p>
 */
public final class IdenTelephones {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("iden_decor", "telephone");

    private IdenTelephones() {
    }

    @Nullable
    public static Supplier<? extends Block> blockSupplier() {
        boolean pg = PowerGridCompat.present();
        boolean cee = ElectroEnergeticsCompat.present();
        if (pg && cee) {
            return interop();
        }
        if (pg) {
            return pgOnly();
        }
        if (cee) {
            return ceeOnly();
        }
        return null;
    }

    private static Supplier<IdenInteropTelephoneBlock> interop() {
        return IdenInteropTelephoneBlock::new;
    }

    private static Supplier<IdenPgTelephoneBlock> pgOnly() {
        return IdenPgTelephoneBlock::new;
    }

    private static Supplier<IdenCeeTelephoneBlock> ceeOnly() {
        return IdenCeeTelephoneBlock::new;
    }

    /** The registered telephone block (CIO's stand-in, once swapped). */
    public static Block block() {
        return BuiltInRegistries.BLOCK.get(ID);
    }
}
