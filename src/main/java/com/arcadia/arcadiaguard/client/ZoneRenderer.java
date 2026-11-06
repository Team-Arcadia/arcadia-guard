package com.arcadia.arcadiaguard.client;

import com.arcadia.arcadiaguard.network.ZoneDataPayload.ClientZoneInfo;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;


@OnlyIn(Dist.CLIENT)
public final class ZoneRenderer {

    private ZoneRenderer() {}

    /** 1.20.1: RenderStateShard constants are protected; expose them through a subclass. */
    private static final class Shards extends RenderType {
        private Shards() { super("arcadiaguard_dummy", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false, () -> {}, () -> {}); }
        static final ShaderStateShard RENDERTYPE_LINES_SHADER = RenderStateShard.RENDERTYPE_LINES_SHADER;
        static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY = RenderStateShard.TRANSLUCENT_TRANSPARENCY;
        static final OutputStateShard ITEM_ENTITY_TARGET = RenderStateShard.ITEM_ENTITY_TARGET;
        static final WriteMaskStateShard COLOR_DEPTH_WRITE = RenderStateShard.COLOR_DEPTH_WRITE;
        static final DepthTestStateShard NO_DEPTH_TEST = RenderStateShard.NO_DEPTH_TEST;
        static final CullStateShard NO_CULL = RenderStateShard.NO_CULL;
        static LineStateShard line(double w) { return new LineStateShard(OptionalDouble.of(w)); }
    }

    /** RenderType for root zones (thick line, no depth test). */
    private static final RenderType AG_LINES_ROOT = RenderType.create(
        "arcadiaguard_lines_root",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        256,
        false, false,
        RenderType.CompositeState.builder()
            .setShaderState(Shards.RENDERTYPE_LINES_SHADER)
            .setLineState(Shards.line(4.0))
            .setTransparencyState(Shards.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(Shards.ITEM_ENTITY_TARGET)
            .setWriteMaskState(Shards.COLOR_DEPTH_WRITE)
            .setDepthTestState(Shards.NO_DEPTH_TEST)
            .setCullState(Shards.NO_CULL)
            .createCompositeState(false)
    );

    /** RenderType for sub-zones (thinner line, no depth test). */
    private static final RenderType AG_LINES_SUB = RenderType.create(
        "arcadiaguard_lines_sub",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        256,
        false, false,
        RenderType.CompositeState.builder()
            .setShaderState(Shards.RENDERTYPE_LINES_SHADER)
            .setLineState(Shards.line(2.5))
            .setTransparencyState(Shards.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(Shards.ITEM_ENTITY_TARGET)
            .setWriteMaskState(Shards.COLOR_DEPTH_WRITE)
            .setDepthTestState(Shards.NO_DEPTH_TEST)
            .setCullState(Shards.NO_CULL)
            .createCompositeState(false)
    );

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        List<ClientZoneInfo> zones = ClientZoneCache.zones();
        if (zones.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        var camera = event.getCamera();
        double cx = camera.getPosition().x;
        double cy = camera.getPosition().y;
        double cz = camera.getPosition().z;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Root zones (thick line)
        var consumerRoot = bufferSource.getBuffer(AG_LINES_ROOT);
        for (ClientZoneInfo zone : zones) {
            if (zone.dimensional()) continue;
            if (zone.hasParent()) continue;
            AABB aabb = new AABB(
                zone.minX() - cx,     zone.minY() - cy,     zone.minZ() - cz,
                zone.maxX() + 1 - cx, zone.maxY() + 1 - cy, zone.maxZ() + 1 - cz
            );
            LevelRenderer.renderLineBox(poseStack, consumerRoot, aabb, 1f, 1f, 1f, 0.8f);
        }
        bufferSource.endBatch(AG_LINES_ROOT);

        // Sub-zones (thinner line)
        var consumerSub = bufferSource.getBuffer(AG_LINES_SUB);
        for (ClientZoneInfo zone : zones) {
            if (zone.dimensional()) continue;
            if (!zone.hasParent()) continue;
            AABB aabb = new AABB(
                zone.minX() - cx,     zone.minY() - cy,     zone.minZ() - cz,
                zone.maxX() + 1 - cx, zone.maxY() + 1 - cy, zone.maxZ() + 1 - cz
            );
            LevelRenderer.renderLineBox(poseStack, consumerSub, aabb, 0.7f, 0.3f, 1f, 0.8f);
        }
        bufferSource.endBatch(AG_LINES_SUB);
    }
}
