package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.item.DynamicItemBlockList;
import com.arcadia.arcadiaguard.handler.handlers.ArsNouveauHandler;
import com.arcadia.arcadiaguard.handler.handlers.ApotheosisCharmHandler;
import com.arcadia.arcadiaguard.handler.handlers.ApotheosisHandler;
import com.arcadia.arcadiaguard.handler.handlers.BetterArcheologyHandler;
import com.arcadia.arcadiaguard.handler.handlers.EmotecraftHandler;
import com.arcadia.arcadiaguard.handler.handlers.MutantMonstersHandler;
import com.arcadia.arcadiaguard.handler.handlers.ParcoolHandler;
import com.arcadia.arcadiaguard.handler.handlers.SpawnBookHandler;
import com.arcadia.arcadiaguard.handler.handlers.TwilightForestHandler;
import com.arcadia.arcadiaguard.handler.handlers.ZoneItemHandler;
import com.arcadia.arcadiaguard.handler.handlers.IronsSpellbooksHandler;
import com.arcadia.arcadiaguard.handler.handlers.OccultismHandler;
import com.arcadia.arcadiaguard.handler.handlers.SimplySwordsHandler;
import com.arcadia.arcadiaguard.handler.handlers.SophisticatedStorageHandler;
import com.arcadia.arcadiaguard.handler.handlers.SupplementariesHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.List;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;

public final class HandlerRegistry {

    private final GuardService guardService;
    private final BlockEventHandler blockEventHandler;
    private final EntityEventHandler entityEventHandler;
    private final PlayerEventHandler playerEventHandler;
    private final FlagEventHandler flagEventHandler;
    private final VanillaExtraHandler vanillaExtraHandler;
    private final MutantMonstersHandler mutantMonstersHandler;
    private final TwilightForestHandler twilightForestHandler;
    private final SophisticatedStorageHandler sophisticatedStorageHandler;
    private final SimplySwordsHandler simplySwordsHandlerRef;
    private final List<Object> handlers;

    // H-P8: pre-typed arrays for O(1) dispatch without instanceof on every event
    private RightClickItemHandler[] rightClickItemHandlers;
    private RightClickBlockHandler[] rightClickBlockHandlers;
    private EntityInteractHandler[] entityInteractHandlers;
    private BlockBreakHandler[] blockBreakHandlers;

    public HandlerRegistry(GuardService guardService, DynamicItemBlockList dynamicItemBlockList) {
        this.guardService = guardService;
        this.blockEventHandler = new BlockEventHandler(guardService);
        this.entityEventHandler = new EntityEventHandler(guardService);
        this.flagEventHandler = new FlagEventHandler(guardService);
        this.mutantMonstersHandler = new MutantMonstersHandler(guardService);
        this.twilightForestHandler = new TwilightForestHandler(guardService);
        this.sophisticatedStorageHandler = new SophisticatedStorageHandler(guardService);
        SimplySwordsHandler simplySwordsHandler = new SimplySwordsHandler(guardService);
        this.simplySwordsHandlerRef = simplySwordsHandler;
        ApotheosisCharmHandler charmHandler = new ApotheosisCharmHandler(guardService);
        this.playerEventHandler = new PlayerEventHandler(guardService, charmHandler);
        this.vanillaExtraHandler = new VanillaExtraHandler(guardService);
        this.handlers = List.of(
            this.playerEventHandler,
            new IronsSpellbooksHandler(guardService),
            new ArsNouveauHandler(guardService),
            simplySwordsHandler,
            new OccultismHandler(guardService),
            new SupplementariesHandler(guardService),
            new ApotheosisHandler(guardService),
            new BetterArcheologyHandler(guardService),
            new SpawnBookHandler(guardService),
            new ZoneItemHandler(guardService, dynamicItemBlockList),
            new ParcoolHandler(guardService),
            charmHandler
        );
        // H-P8: build typed arrays so dispatch loops cast once at boot, not on every event
        this.rightClickItemHandlers = this.handlers.stream()
            .filter(h -> h instanceof RightClickItemHandler)
            .toArray(RightClickItemHandler[]::new);
        this.rightClickBlockHandlers = this.handlers.stream()
            .filter(h -> h instanceof RightClickBlockHandler)
            .toArray(RightClickBlockHandler[]::new);
        this.entityInteractHandlers = this.handlers.stream()
            .filter(h -> h instanceof EntityInteractHandler)
            .toArray(EntityInteractHandler[]::new);
        this.blockBreakHandlers = this.handlers.stream()
            .filter(h -> h instanceof BlockBreakHandler)
            .toArray(BlockBreakHandler[]::new);
    }

    public void register(IEventBus ignored) {
        // H-P7: register static event listeners for cache invalidation
        IronsSpellbooksHandler.registerEventListeners();
        // Mod integrations that register their own listeners
        EmotecraftHandler.register(guardService);
        new com.arcadia.arcadiaguard.handler.handlers.CarryOnHandler(guardService).register();

        for (Object handler : this.handlers) {
            if (handler instanceof DynamicEventHandler dynamic) {
                registerDynamic(dynamic);
            }
        }
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayerInteractEvent.RightClickBlock.class, blockEventHandler::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.BreakEvent.class, this::onBlockBreak);
        // PORT-TODO: BlockDropsEvent absent on Forge 1.20.1 (see BlockEventHandler).
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.EntityPlaceEvent.class, blockEventHandler::onBlockPlace);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayerInteractEvent.LeftClickBlock.class, playerEventHandler::onLeftClickBlock);
        MinecraftForge.EVENT_BUS.addListener(playerEventHandler::onDimensionChange);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, playerEventHandler::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class, EasterEggHandler::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, EasterEggHandler::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, EasterEggHandler::onServerTick);
        // VanillaExtraHandler : flags vanilla additionnels
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.PlayerTickEvent.class, vanillaExtraHandler::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, net.minecraftforge.event.entity.EntityTravelToDimensionEvent.class, vanillaExtraHandler::onTravelDim);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, true, BlockEvent.BlockToolModificationEvent.class, vanillaExtraHandler::onBlockToolModification);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, true, PlayerInteractEvent.RightClickBlock.class, vanillaExtraHandler::onBucketUse);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, true, PlayerInteractEvent.RightClickItem.class, vanillaExtraHandler::onBucketUseItem);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, true, net.minecraftforge.event.ServerChatEvent.class, vanillaExtraHandler::onChat);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, true, net.minecraftforge.event.CommandEvent.class, vanillaExtraHandler::onCommand);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingAttackEvent.class, entityEventHandler::onLivingIncomingDamage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, net.minecraftforge.event.entity.living.LivingDeathEvent.class, entityEventHandler::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingFallEvent.class, entityEventHandler::onLivingFall);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobSpawnEvent.FinalizeSpawn.class, entityEventHandler::onMobSpawn);
        // LOWEST : cancel le spawn si un autre mod a discard l'entite (anti-spam Hominid-like).
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, MobSpawnEvent.FinalizeSpawn.class, entityEventHandler::onFinalizeSpawnDiscardCleanup);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ExplosionEvent.Detonate.class, entityEventHandler::onExplosion);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.PlayerTickEvent.class, playerEventHandler::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingEvent.LivingTickEvent.class, entityEventHandler::onEntityTick);

        // FlagEventHandler : interactions / combats / items / téléportations / croissance
        // S-H16 T1 : Sophisticated Storage en HIGHEST — doit s'executer avant FlagEventHandler
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class, sophisticatedStorageHandler::onRightClickBlock);
        // S-H16 AC1 : minecarts-coffres et chest-boats (Entity, pas BlockEntity)
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.EntityInteract.class, sophisticatedStorageHandler::onEntityInteract);
        // S-H16 T7 : SimplySwords abilities declenchees au chargement de l'item
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
            net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start.class,
            simplySwordsHandlerRef::onUseItemStart);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickBlock.class, flagEventHandler::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickItem.class, flagEventHandler::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.EntityInteract.class, flagEventHandler::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, AttackEntityEvent.class, flagEventHandler::onAttackEntity);
        // Per-zone banned items : bloque aussi l'attaque (epee bannie dans la zone ne doit pas frapper)
        ZoneItemHandler zoneItems = this.handlers.stream()
            .filter(ZoneItemHandler.class::isInstance).map(ZoneItemHandler.class::cast).findFirst().orElse(null);
        if (zoneItems != null) {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, AttackEntityEvent.class, zoneItems::onAttack);
        }
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ItemTossEvent.class, flagEventHandler::onItemToss);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityItemPickupEvent.class, flagEventHandler::onItemPickup);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingExperienceDropEvent.class, flagEventHandler::onExpDrop);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.EnderPearl.class, flagEventHandler::onEnderPearlTeleport);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.ChorusFruit.class, flagEventHandler::onChorusFruitTeleport);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.CropGrowEvent.Pre.class, flagEventHandler::onCropGrow);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BonemealEvent.class, flagEventHandler::onBonemeal);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, SaplingGrowTreeEvent.class, flagEventHandler::onTreeGrow);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.FarmlandTrampleEvent.class, flagEventHandler::onFarmlandTrample);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.FluidPlaceBlockEvent.class, flagEventHandler::onFluidPlaceBlock);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, flagEventHandler::onBlockEntityPlace);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityJoinLevelEvent.class, entityEventHandler::onMobJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, entityEventHandler::onAnimalJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityJoinLevelEvent.class, flagEventHandler::onVehicleJoin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, mutantMonstersHandler::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ProjectileImpactEvent.class, twilightForestHandler::onProjectileImpact);
    }

    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        // H-P8: iterate pre-typed array — no instanceof check on every element
        for (RightClickItemHandler h : rightClickItemHandlers) {
            if (event.isCanceled()) break;
            h.handle(event);
        }
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        for (RightClickBlockHandler h : rightClickBlockHandlers) {
            if (event.isCanceled()) break;
            h.handle(event);
        }
    }

    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        for (EntityInteractHandler h : entityInteractHandlers) {
            if (event.isCanceled()) break;
            h.handle(event);
        }
    }

    public void onBlockBreak(BlockEvent.BreakEvent event) {
        blockEventHandler.onBlockBreak(event);
        if (event.isCanceled()) return;
        for (BlockBreakHandler h : blockBreakHandlers) {
            h.handle(event);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void registerDynamic(DynamicEventHandler handler) {
        Object eventClass = ReflectionHelper.invokeStatic("java.lang.Class", "forName",
            new Class<?>[] { String.class }, handler.eventClassName()).orElse(null);
        if (!(eventClass instanceof Class<?> clazz)) return;
        // H11: guard against non-Event classes being passed to addListener
        if (!net.minecraftforge.eventbus.api.Event.class.isAssignableFrom(clazz)) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Skipping handler {}: {} is not an Event subclass",
                handler.getClass().getSimpleName(), clazz.getName());
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class) clazz, event -> handler.handle((Event) event));
    }

    public interface DynamicEventHandler {
        String eventClassName();
        void handle(Event event);
    }

    public interface RightClickItemHandler { void handle(PlayerInteractEvent.RightClickItem event); }
    public interface RightClickBlockHandler { void handle(PlayerInteractEvent.RightClickBlock event); }
    public interface EntityInteractHandler { void handle(PlayerInteractEvent.EntityInteract event); }
    public interface BlockBreakHandler { void handle(BlockEvent.BreakEvent event); }
}
