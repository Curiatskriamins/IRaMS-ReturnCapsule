package com.curiats.returncapsule.client.geo;

 
public final class GeoModel {
    private static final GeoFace[] NO_FACES = new GeoFace[0];

    private final GeoFace[] staticFaces;
    private final GeoFace[] lidFaces;
    private final float centerX;
    private final float centerY;
    private final float centerZ;
    private final float lidOffsetX;
    private final float lidOffsetY;
    private final float lidOffsetZ;
    private final boolean glow;
    private final int faceCount;

    GeoModel(GeoFace[] staticFaces, GeoFace[] lidFaces,
             float centerX, float centerY, float centerZ,
             float lidOffsetX, float lidOffsetY, float lidOffsetZ,
             boolean glow) {
        this.staticFaces = staticFaces;
        this.lidFaces = lidFaces;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.lidOffsetX = lidOffsetX;
        this.lidOffsetY = lidOffsetY;
        this.lidOffsetZ = lidOffsetZ;
        this.glow = glow;
        this.faceCount = staticFaces.length + lidFaces.length;
    }

    static GeoModel empty(boolean glow) {
        return new GeoModel(NO_FACES, NO_FACES, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, glow);
    }

    public boolean isEmpty() {
        return this.faceCount == 0;
    }

    public boolean hasGlow() {
        return this.glow;
    }

    public int faceCount() {
        return this.faceCount;
    }

    public GeoFace[] staticFaces() {
        return this.staticFaces;
    }

    public GeoFace[] lidFaces() {
        return this.lidFaces;
    }

    public float centerX() {
        return this.centerX;
    }

    public float centerY() {
        return this.centerY;
    }

    public float centerZ() {
        return this.centerZ;
    }

    public float lidOffsetX() {
        return this.lidOffsetX;
    }

    public float lidOffsetY() {
        return this.lidOffsetY;
    }

    public float lidOffsetZ() {
        return this.lidOffsetZ;
    }
}
