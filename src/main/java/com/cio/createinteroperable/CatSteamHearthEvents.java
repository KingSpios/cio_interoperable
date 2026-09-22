package com.cio.createinteroperable;

import net.minecraft.world.entity.animal.Cat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Wires {@link CatSteamHearthGoal} onto every vanilla {@link Cat} the moment
 * it joins a (server-side) level — the same "inject an extra goal onto a
 * vanilla mob from outside" technique any Create-family/vanilla-AI-extending
 * mod uses, since {@code Cat#registerGoals} itself is vanilla code this
 * project doesn't own and can't edit or Mixin into cleanly (goal lists are
 * built once, inline, in that method).
 * <p>
 * {@link EntityJoinLevelEvent} fires again every time a cat re-enters a
 * loaded level — on chunk (re)load in particular, NOT just first spawn — so
 * this deliberately checks {@code goalSelector.getAvailableGoals()} for an
 * existing {@link CatSteamHearthGoal} first ({@link
 * net.minecraft.world.entity.ai.goal.WrappedGoal#getGoal()} is public
 * specifically for this kind of external inspection) rather than adding
 * unconditionally — without that check, a cat that never truly despawns
 * would accumulate one extra duplicate instance of this goal every time its
 * chunk unloads and reloads.
 */
@EventBusSubscriber(modid = CreateInteroperable.ID)
public final class CatSteamHearthEvents {
    private CatSteamHearthEvents() {
    }

    @SubscribeEvent
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Cat cat)) {
            return;
        }
        boolean alreadyPresent = cat.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof CatSteamHearthGoal);
        if (!alreadyPresent) {
            // Same priority slot vanilla itself uses for the closely-related
            // CatSitOnBlockGoal (a lit furnace/chest/bed) — lower priority
            // than sitting-when-ordered, owner-bed-relaxing, and following the
            // owner, but still above idle random strolling.
            cat.goalSelector.addGoal(7, new CatSteamHearthGoal(cat));
        }
    }
}
