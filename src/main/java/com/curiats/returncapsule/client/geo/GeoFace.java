package com.curiats.returncapsule.client.geo;

/**
 * One baked quad of the capsule geometry, in model space.
 *
 * <p>Everything the renderer needs is precomputed when the model is loaded: the four vertices,
 * the eight texture coordinates and the unit normal. The old renderer recomputed the normal
 * from a {@code Direction} and allocated a {@code Vector3f} for every face of every frame.</p>
 */
public final class GeoFace {
    private final float[] positions;
    private final float[] uvs;
    private final float normalX;
    private final float normalY;
    private final float normalZ;

    public GeoFace(float[] positions, float[] uvs, float normalX, float normalY, float normalZ) {
        this.positions = positions;
        this.uvs = uvs;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
    }

    /** 12 floats: four vertices of three components. */
    public float[] positions() {
        return this.positions;
    }

    /** 8 floats: four texture coordinate pairs. */
    public float[] uvs() {
        return this.uvs;
    }

    public float normalX() {
        return this.normalX;
    }

    public float normalY() {
        return this.normalY;
    }

    public float normalZ() {
        return this.normalZ;
    }
}
