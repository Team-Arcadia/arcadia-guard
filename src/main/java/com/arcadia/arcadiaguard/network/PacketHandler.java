package com.arcadia.arcadiaguard.network;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.handler.GuiActionHandler;
import com.arcadia.arcadiaguard.network.gui.DimFlagsPayload;
import com.arcadia.arcadiaguard.network.gui.EmoteBlockedPayload;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import com.arcadia.arcadiaguard.network.gui.JadeOverlayBlockedPayload;
import com.arcadia.arcadiaguard.network.gui.OpenGuiPayload;
import com.arcadia.arcadiaguard.network.gui.ParcoolBlockedPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneLogsPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneRemovedPayload;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Canal reseau Forge 1.20.1 (SimpleChannel). */
public final class PacketHandler {

    private PacketHandler() {}

    private static final Logger LOGGER = LogManager.getLogger(PacketHandler.class);

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(ArcadiaGuard.MOD_ID, "main"),
        () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static final AtomicBoolean registered = new AtomicBoolean(false);
    private static final AtomicBoolean warnedDefaultHandler = new AtomicBoolean(false);

    private static <T> BiConsumer<T, NetworkEvent.Context> defaultHandler(String name) {
        return (p, c) -> {
            if (warnedDefaultHandler.compareAndSet(false, true)) {
                LOGGER.warn("Default handler called for {} - wiring not initialized", name);
            }
        };
    }

    // Handlers S->C injectes par ArcadiaGuardClient.init() (CLIENT dist uniquement)
    private static BiConsumer<ZoneDataPayload,    NetworkEvent.Context> clientZoneData    = defaultHandler("ZoneDataPayload");
    private static BiConsumer<OpenGuiPayload,     NetworkEvent.Context> clientOpenGui     = defaultHandler("OpenGuiPayload");
    private static BiConsumer<ZoneDetailPayload,  NetworkEvent.Context> clientZoneDetail  = defaultHandler("ZoneDetailPayload");
    private static BiConsumer<DimFlagsPayload,    NetworkEvent.Context> clientDimFlags    = defaultHandler("DimFlagsPayload");
    private static BiConsumer<ZoneLogsPayload,    NetworkEvent.Context> clientZoneLogs    = defaultHandler("ZoneLogsPayload");
    private static BiConsumer<ZoneRemovedPayload, NetworkEvent.Context> clientZoneRemoved = defaultHandler("ZoneRemovedPayload");

    public static void setClientHandlers(
            BiConsumer<ZoneDataPayload,    NetworkEvent.Context> zoneData,
            BiConsumer<OpenGuiPayload,     NetworkEvent.Context> openGui,
            BiConsumer<ZoneDetailPayload,  NetworkEvent.Context> zoneDetail,
            BiConsumer<DimFlagsPayload,    NetworkEvent.Context> dimFlags,
            BiConsumer<ZoneLogsPayload,    NetworkEvent.Context> zoneLogs,
            BiConsumer<ZoneRemovedPayload, NetworkEvent.Context> zoneRemoved) {
        clientZoneData    = zoneData;
        clientOpenGui     = openGui;
        clientZoneDetail  = zoneDetail;
        clientDimFlags    = dimFlags;
        clientZoneLogs    = zoneLogs;
        clientZoneRemoved = zoneRemoved;
    }

    /** Appele depuis le constructeur du mod ; l'enregistrement se fait au common setup. */
    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(PacketHandler::registerMessages));
    }

    /** Enregistre tous les messages (idempotent). */
    public static void registerMessages() {
        if (!registered.compareAndSet(false, true)) return;
        int id = 0;

        s2c(id++, ZoneDataPayload.class,   ZoneDataPayload.STREAM_CODEC,   (p, c) -> clientZoneData.accept(p, c));
        s2c(id++, OpenGuiPayload.class,    OpenGuiPayload.STREAM_CODEC,    (p, c) -> clientOpenGui.accept(p, c));
        s2c(id++, ZoneDetailPayload.class, ZoneDetailPayload.STREAM_CODEC, (p, c) -> clientZoneDetail.accept(p, c));
        s2c(id++, DimFlagsPayload.class,   DimFlagsPayload.STREAM_CODEC,   (p, c) -> clientDimFlags.accept(p, c));
        s2c(id++, ZoneLogsPayload.class,   ZoneLogsPayload.STREAM_CODEC,   (p, c) -> clientZoneLogs.accept(p, c));

        // S-H21 : parcool blocked state S->C (client-only handler inline)
        s2c(id++, ParcoolBlockedPayload.class, ParcoolBlockedPayload.STREAM_CODEC,
            (p, c) -> com.arcadia.arcadiaguard.client.ClientParcoolState.setBlocked(p.blocked()));

        // Emotecraft : emote blocked state S->C
        s2c(id++, EmoteBlockedPayload.class, EmoteBlockedPayload.STREAM_CODEC,
            (p, c) -> com.arcadia.arcadiaguard.client.ClientEmoteBlockedState.setBlocked(p.blocked()));

        // Jade : overlay blocked state S->C
        s2c(id++, JadeOverlayBlockedPayload.class, JadeOverlayBlockedPayload.STREAM_CODEC,
            (p, c) -> com.arcadia.arcadiaguard.client.ClientJadeOverlayState.setBlocked(p.blocked()));

        // Zone deleted S->C : purge du ClientZoneCache (rendu)
        s2c(id++, ZoneRemovedPayload.class, ZoneRemovedPayload.STREAM_CODEC,
            (p, c) -> clientZoneRemoved.accept(p, c));

        CHANNEL.messageBuilder(GuiActionPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder((p, buf) -> GuiActionPayload.STREAM_CODEC.write(buf, p))
            .decoder(GuiActionPayload.STREAM_CODEC::read)
            .consumerMainThread(wrap(GuiActionHandler::handle))
            .add();
    }

    private static <T> void s2c(int id, Class<T> type, NetCodec<T> codec, BiConsumer<T, NetworkEvent.Context> handler) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p, buf) -> codec.write(buf, p))
            .decoder(codec::read)
            .consumerMainThread(wrap(handler))
            .add();
    }

    /** Adapte un handler (payload, ctx) en consumer Forge et marque le paquet traite. */
    private static <T> BiConsumer<T, Supplier<NetworkEvent.Context>> wrap(BiConsumer<T, NetworkEvent.Context> handler) {
        return (p, sup) -> {
            NetworkEvent.Context ctx = sup.get();
            handler.accept(p, ctx);
            ctx.setPacketHandled(true);
        };
    }

    // --- Envois ---

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
