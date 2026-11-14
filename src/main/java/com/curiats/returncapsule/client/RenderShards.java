package com.curiats.returncapsule.client;

import net.minecraft.client.renderer.RenderStateShard;

public final class RenderShards extends RenderStateShard {
    public static final ShaderStateShard ENTITY_TRANSLUCENT =
            RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
    public static final TransparencyStateShard TRANSLUCENT =
            RenderStateShard.TRANSLUCENT_TRANSPARENCY;
    public static final DepthTestStateShard NO_DEPTH = RenderStateShard.NO_DEPTH_TEST;
    public static final CullStateShard CULL_NONE = RenderStateShard.NO_CULL;
    public static final LightmapStateShard LIGHTMAP = RenderStateShard.LIGHTMAP;
    public static final OverlayStateShard OVERLAY = RenderStateShard.OVERLAY;

    private RenderShards() {
        super("returncapsule_render_shards", () -> {
        }, () -> {
        });
    }
}
