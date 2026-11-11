package com.curiats.returncapsule.client.geo;

 
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

     
    public float[] positions() {
        return this.positions;
    }

     
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
