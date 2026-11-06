package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.BlockBreakHandler;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.RightClickBlockHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

public final class ApotheosisHandler implements BlockBreakHandler, RightClickBlockHandler {

    // PORT: Apotheosis 7.4.8 (1.20.1) : classes sous dev.shadowsoffire.apotheosis.adventure.*,
    // pas de RadialUtil/RadialBonus. RadialAffix.breakBlockRadius calcule les blocs en interne.
    private static final String RADIAL_AFFIX_CLASS = "dev.shadowsoffire.apotheosis.adventure.affix.effect.RadialAffix";
    private static final String RADIAL_STATE_CLASS = "dev.shadowsoffire.apotheosis.adventure.affix.effect.RadialAffix$RadialState";
    private static final String AFFIX_HELPER_CLASS = "dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper";
    /** Rayon max d'un RadialAffix 1.20.1 (5x5 + offsets) : verification conservatrice. */
    private static final int RADIAL_MAX_RADIUS = 2;
    private final GuardService guardService;

    public ApotheosisHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(BlockEvent.BreakEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_APOTHEOSIS_ENCHANTS.get()) return;
        if (!ModList.get().isLoaded("apotheosis")) return;
        Object playerObj = ReflectionHelper.invoke(event, "getPlayer", new Class<?>[0]).orElse(null);
        if (!(playerObj instanceof ServerPlayer player)) return;
        Object posObj = ReflectionHelper.invoke(event, "getPos", new Class<?>[0]).orElse(null);
        if (!(posObj instanceof BlockPos pos)) return;
        Object playerRef = playerObj;
        Object posRef = posObj;

        Object toolObj = ReflectionHelper.invoke(player, "getMainHandItem", new Class<?>[0]).orElse(ItemStack.EMPTY);
        if (!(toolObj instanceof ItemStack tool) || tool.isEmpty()) return;

        if (!hasRadialAffix(tool) || !radialMiningEnabled(player)) return;

        if (this.guardService.blockIfProtected(player, pos, "apotheosis:tunneling", "apotheosis", ArcadiaGuardConfig.MESSAGE_APOTHEOSIS.get()).blocked()) {
            event.setCanceled(true);
            return;
        }

        // PORT: pas de RadialUtil.getBrokenBlocks en 1.20.1 -> on verifie la boite max autour du bloc.
        for (BlockPos extraPos : BlockPos.betweenClosed(pos.offset(-RADIAL_MAX_RADIUS, -RADIAL_MAX_RADIUS, -RADIAL_MAX_RADIUS),
                pos.offset(RADIAL_MAX_RADIUS, RADIAL_MAX_RADIUS, RADIAL_MAX_RADIUS))) {
            if (extraPos.equals(pos)) continue;
            if (this.guardService.blockIfProtected(player, extraPos.immutable(), "apotheosis:tunneling", "apotheosis", ArcadiaGuardConfig.MESSAGE_APOTHEOSIS.get()).blocked()) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // Affix "Enlightened" — pose une torche via RightClickBlock en échange de durabilité.
    // L'outil en main est n'importe quel item avec un affix Apotheosis (pioche vanilla, etc.)
    // → on vérifie la présence du data component "apotheosis:affixes", pas le namespace de l'item.
    @Override
    public void handle(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_APOTHEOSIS_ENCHANTS.get()) return;
        if (!ModList.get().isLoaded("apotheosis")) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        if (!(entity instanceof ServerPlayer player)) return;

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        // Vérifie si l'item a des affixes Apotheosis via le data component "apotheosis:affixes"
        if (!hasApotheosisAffixes(tool)) return;

        Object hitResult = ReflectionHelper.invoke(event, "getHitVec", new Class<?>[0]).orElse(null);
        Object blockPos = hitResult == null ? null : ReflectionHelper.invoke(hitResult, "getBlockPos", new Class<?>[0]).orElse(null);
        if (!(blockPos instanceof BlockPos pos)) return;

        if (this.guardService.blockIfProtected(player, pos, "apotheosis:enlightened", "apotheosis", ArcadiaGuardConfig.MESSAGE_APOTHEOSIS.get()).blocked()) {
            event.setCanceled(true);
        }
    }

    private boolean hasApotheosisAffixes(ItemStack stack) {
        // AffixHelper.hasAffixes(ItemStack) — retourne true si l'item a des affixes Apotheosis.
        // L'outil peut être n'importe quel item (pioche vanilla, etc.) avec un data component Apotheosis.
        Object result = ReflectionHelper.invokeStatic(
            AFFIX_HELPER_CLASS,
            "hasAffixes",
            new Class<?>[] { ItemStack.class },
            stack
        ).orElse(Boolean.FALSE);
        return Boolean.TRUE.equals(result);
    }

    /** AffixHelper.getAffixes(ItemStack) -> Map<DynamicHolder<Affix>, AffixInstance> ; holder.get() instanceof RadialAffix. */
    private boolean hasRadialAffix(ItemStack tool) {
        try {
            Class<?> radialCls = Class.forName(RADIAL_AFFIX_CLASS);
            Object map = ReflectionHelper.invokeStatic(AFFIX_HELPER_CLASS, "getAffixes", new Class<?>[] { ItemStack.class }, tool).orElse(null);
            if (!(map instanceof java.util.Map<?, ?> affixes)) return false;
            for (Object holder : affixes.keySet()) {
                Object affix = ReflectionHelper.invoke(holder, "get", new Class<?>[0]).orElse(null);
                if (radialCls.isInstance(affix)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** RadialState.getState(Player).isRadialMiningEnabled(Player) ; true si la reflexion echoue (prudence). */
    private boolean radialMiningEnabled(ServerPlayer player) {
        Object state = ReflectionHelper.invokeStatic(RADIAL_STATE_CLASS, "getState",
            new Class<?>[] { net.minecraft.world.entity.player.Player.class }, player).orElse(null);
        if (state == null) return true;
        Object enabled = ReflectionHelper.invoke(state, "isRadialMiningEnabled",
            new Class<?>[] { net.minecraft.world.entity.player.Player.class }, player).orElse(Boolean.TRUE);
        return !Boolean.FALSE.equals(enabled);
    }
}
