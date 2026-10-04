package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.iden.IdenFluorescentLightBlock;
import com.cio.createinteroperable.iden.IdenTelephones;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * Swaps CIO classes in for two Iden's Decor blocks at Iden's own
 * {@code BLOCKS.register(name, supplier)} call (NeoForge has no registry
 * overrides): the Fluorescent Light Block ({@link IdenFluorescentLightBlock}
 * &mdash; a bare vanilla {@code Block} on Iden's side, so there is no Iden class
 * to mix into) and the telephone ({@link IdenTelephones} &mdash; it needs Power
 * Grid's {@code ElectricBlock} as its superclass for a CPG tap, which a mixin
 * can't give it). Same ids, properties and blockstate names, so worlds,
 * recipes, loot and models are untouched; every other block Iden registers
 * passes straight through.
 *
 * <p>Name-targeted, non-required &mdash; a no-op without Iden's Decor.</p>
 */
@Mixin(targets = "net.identidade.iden_decor.block.ModBlocks", remap = false)
public abstract class IdenModBlocksMixin {

    @Redirect(method = "registerBlock", remap = false, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/registries/DeferredRegister$Blocks;register(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/neoforged/neoforge/registries/DeferredBlock;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DeferredBlock cio$swapIdenBlocks(DeferredRegister.Blocks blocks, String name, Supplier supplier) {
        if ("fluorescent_light_block".equals(name)) {
            return blocks.register(name, IdenFluorescentLightBlock::new);
        }
        if ("telephone".equals(name)) {
            Supplier telephone = IdenTelephones.blockSupplier();
            if (telephone != null) {
                return blocks.register(name, telephone);
            }
        }
        return blocks.register(name, supplier);
    }
}
