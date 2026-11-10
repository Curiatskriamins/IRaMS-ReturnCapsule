package com.curiats.returncapsule.client.geo;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a Bedrock style {@code .geo.json} model and bakes it into {@link GeoModel}.
 *
 * <p>The old {@code CapsuleGeoModel} mixed JSON parsing, bone tree handling, bounds maths and
 * rendering in one 490 line class. Parsing, baking and drawing are now separate, and the bake
 * step means the per frame cost no longer depends on the bone hierarchy.</p>
 */
public final class GeoModelLoader {
    private static final Logger LOGGER = ReturnCapsuleMod.LOGGER;
    private static final String LID_BONE = "GJ1";
    private static final float LID_SHIFT_X = -2.0F;
    private static final float LID_SHIFT_Y = 14.0F;
    private static final float LID_SHIFT_Z = -4.0F;
    private static final float DEFAULT_TEXTURE_SIZE = 16.0F;

    public static GeoModel load(ResourceLocation geo, boolean glow) {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(geo).orElse(null);
            if (resource == null) {
                LOGGER.error("Missing capsule geometry {}", geo);
                return GeoModel.empty(glow);
            }
            try (Reader reader = resource.openAsReader()) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject(), geo, glow);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to load capsule geometry {}", geo, exception);
            return GeoModel.empty(glow);
        }
    }

    private static GeoModel parse(JsonObject root, ResourceLocation geo, boolean glow) {
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        float textureWidth = readFloat(description, "texture_width", DEFAULT_TEXTURE_SIZE);
        float textureHeight = readFloat(description, "texture_height", DEFAULT_TEXTURE_SIZE);

        Map<String, RawBone> bones = new LinkedHashMap<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject object = element.getAsJsonObject();
            String name = object.get("name").getAsString();
            String parent = object.has("parent") ? object.get("parent").getAsString() : null;
            float[] pivot = readVec3(object.get("pivot"));
            float[] rotation = readVec3(object.get("rotation"));
            RawBone bone = new RawBone(name, parent,
                    -pivot[0], pivot[1], pivot[2],
                    radians(-rotation[0]), radians(-rotation[1]), radians(rotation[2]));
            if (object.has("cubes")) {
                for (JsonElement cubeElement : object.getAsJsonArray("cubes")) {
                    RawCube cube = parseCube(cubeElement.getAsJsonObject(), textureWidth, textureHeight);
                    if (cube != null) {
                        bone.cubes.add(cube);
                    }
                }
            }
            bones.put(name, bone);
        }

        List<RawBone> roots = new ArrayList<>();
        for (RawBone bone : bones.values()) {
            RawBone parent = bone.parentName == null ? null : bones.get(bone.parentName);
            if (parent != null) {
                parent.children.add(bone);
            } else {
                roots.add(bone);
            }
        }

        Baker baker = new Baker(glow);
        Matrix4f identity = new Matrix4f();
        for (RawBone start : roots) {
            baker.bake(start, identity, false);
        }
        GeoModel model = baker.finish(roots.size(), geo);
        return model;
    }

    private static RawCube parseCube(JsonObject object, float textureWidth, float textureHeight) {
        float[] origin = readVec3(object.get("origin"));
        float[] size = readVec3(object.get("size"));
        float[] pivot = readVec3(object.get("pivot"));
        float[] rotation = readVec3(object.get("rotation"));
        boolean mirror = object.has("mirror") && object.get("mirror").getAsBoolean();

        RawCube cube = new RawCube(
                -(origin[0] + size[0]), origin[1], origin[2],
                size[0], size[1], size[2],
                -pivot[0], pivot[1], pivot[2],
                radians(-rotation[0]), radians(-rotation[1]), radians(rotation[2]));

        if (!object.has("uv") || !object.get("uv").isJsonObject()) {
            return cube;
        }
        JsonObject uv = object.getAsJsonObject("uv");
        for (Direction direction : Direction.values()) {
            String key = direction.getName();
            if (!uv.has(key)) {
                continue;
            }
            RawFace face = parseFace(direction, uv.getAsJsonObject(key), cube, mirror, textureWidth, textureHeight);
            if (face != null) {
                cube.faces.add(face);
            }
        }
        return cube;
    }

    private static RawFace parseFace(Direction direction, JsonObject face, RawCube cube, boolean mirror,
                                     float textureWidth, float textureHeight) {
        if (!face.has("uv") || !face.has("uv_size")) {
            return null;
        }
        JsonArray uvArray = face.getAsJsonArray("uv");
        JsonArray sizeArray = face.getAsJsonArray("uv_size");
        float u = uvArray.get(0).getAsFloat();
        float v = uvArray.get(1).getAsFloat();
        float width = sizeArray.get(0).getAsFloat();
        float height = sizeArray.get(1).getAsFloat();
        int rotation = face.has("uv_rotation") ? face.get("uv_rotation").getAsInt() : 0;

        float uMax = u + width;
        float vMax = v + height;
        if (!mirror) {
            float swap = u;
            u = uMax;
            uMax = swap;
        }

        float[] corners = switch (Math.floorMod(rotation, 360)) {
            case 90 -> new float[]{uMax, v, uMax, vMax, u, vMax, u, v};
            case 180 -> new float[]{uMax, vMax, u, vMax, u, v, uMax, v};
            case 270 -> new float[]{u, vMax, uMax, vMax, uMax, v, u, v};
            default -> new float[]{uMax, v, u, v, u, vMax, uMax, vMax};
        };

        float uCenter = (u + uMax) * 0.5F / textureWidth;
        for (int i = 0; i < corners.length; i += 2) {
            corners[i] /= textureWidth;
            corners[i + 1] /= textureHeight;
            corners[i] = 2.0F * uCenter - corners[i];
        }

        Direction renderDirection = direction;
        if (mirror) {
            if (direction == Direction.WEST) {
                renderDirection = Direction.EAST;
            } else if (direction == Direction.EAST) {
                renderDirection = Direction.WEST;
            }
        }
        return new RawFace(renderDirection, facePositions(renderDirection, cube), corners);
    }

    private static float[] facePositions(Direction direction, RawCube cube) {
        float x0 = cube.x;
        float y0 = cube.y;
        float z0 = cube.z;
        float x1 = x0 + cube.width;
        float y1 = y0 + cube.height;
        float z1 = z0 + cube.depth;

        return switch (direction) {
            case WEST -> new float[]{x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1};
            case EAST -> new float[]{x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0};
            case NORTH -> new float[]{x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0};
            case SOUTH -> new float[]{x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1};
            case UP -> new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0};
            case DOWN -> new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1};
        };
    }

    private static float readFloat(JsonObject object, String key, float fallback) {
        return object.has(key) ? object.get(key).getAsFloat() : fallback;
    }

    private static float[] readVec3(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }
        JsonArray array = element.getAsJsonArray();
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    /** Accumulates baked faces while walking the bone tree once. */
    private static final class Baker {
        private final boolean glow;
        private final List<GeoFace> staticFaces = new ArrayList<>();
        private final List<GeoFace> lidFaces = new ArrayList<>();
        private final Vector3f lidShift = new Vector3f();
        private final Vector4f point = new Vector4f();
        private final Vector3f normal = new Vector3f();
        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float minZ = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;
        private float maxZ = -Float.MAX_VALUE;
        private boolean hasBounds;

        Baker(boolean glow) {
            this.glow = glow;
        }

        void bake(RawBone bone, Matrix4f parent, boolean insideLid) {
            Matrix4f local = new Matrix4f(parent)
                    .translate(bone.pivotX, bone.pivotY, bone.pivotZ)
                    .rotateZ(bone.rotZ)
                    .rotateY(bone.rotY)
                    .rotateX(bone.rotX)
                    .translate(-bone.pivotX, -bone.pivotY, -bone.pivotZ);

            boolean lid = insideLid || LID_BONE.equals(bone.name);
            if (lid && !insideLid) {
                // The lid translation happens in the parent's space, so its model space
                // direction is the parent rotation applied to the configured shift.
                Vector3f shift = new Vector3f(LID_SHIFT_X, LID_SHIFT_Y, LID_SHIFT_Z);
                new Matrix3f(parent).transform(shift);
                this.lidShift.set(shift);
            }

            for (RawCube cube : bone.cubes) {
                Matrix4f cubeMatrix = new Matrix4f(local)
                        .translate(cube.pivotX, cube.pivotY, cube.pivotZ)
                        .rotateZ(cube.rotZ)
                        .rotateY(cube.rotY)
                        .rotateX(cube.rotX)
                        .translate(-cube.pivotX, -cube.pivotY, -cube.pivotZ);
                Matrix3f normalMatrix = new Matrix3f(cubeMatrix);
                for (RawFace face : cube.faces) {
                    this.addFace(face, cubeMatrix, normalMatrix, lid);
                }
            }

            for (RawBone child : bone.children) {
                this.bake(child, local, lid);
            }
        }

        private void addFace(RawFace face, Matrix4f matrix, Matrix3f normalMatrix, boolean lid) {
            float[] baked = new float[12];
            for (int i = 0; i < 4; i++) {
                this.point.set(face.positions[i * 3], face.positions[i * 3 + 1], face.positions[i * 3 + 2], 1.0F);
                matrix.transform(this.point);
                baked[i * 3] = this.point.x;
                baked[i * 3 + 1] = this.point.y;
                baked[i * 3 + 2] = this.point.z;
                this.include(this.point.x, this.point.y, this.point.z);
            }
            this.normal.set(face.direction.getStepX(), face.direction.getStepY(), face.direction.getStepZ());
            normalMatrix.transform(this.normal).normalize();
            GeoFace geoFace = new GeoFace(baked, face.uvs, this.normal.x, this.normal.y, this.normal.z);
            (lid ? this.lidFaces : this.staticFaces).add(geoFace);
        }

        private void include(float x, float y, float z) {
            this.hasBounds = true;
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.minZ = Math.min(this.minZ, z);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            this.maxZ = Math.max(this.maxZ, z);
        }

        GeoModel finish(int rootCount, ResourceLocation geo) {
            if (!this.hasBounds) {
                LOGGER.warn("Capsule geometry {} contains no faces", geo);
                return GeoModel.empty(this.glow);
            }
            GeoModel model = new GeoModel(
                    this.staticFaces.toArray(new GeoFace[0]),
                    this.lidFaces.toArray(new GeoFace[0]),
                    (this.minX + this.maxX) * 0.5F,
                    this.minY,
                    (this.minZ + this.maxZ) * 0.5F,
                    this.lidShift.x, this.lidShift.y, this.lidShift.z,
                    this.glow);
            LOGGER.info("Loaded capsule geometry {}: {} root bone(s), {} face(s), height {} units",
                    geo, rootCount, model.faceCount(), this.maxY - this.minY);
            return model;
        }
    }

    private static final class RawBone {
        final String name;
        final String parentName;
        final float pivotX;
        final float pivotY;
        final float pivotZ;
        final float rotX;
        final float rotY;
        final float rotZ;
        final List<RawCube> cubes = new ArrayList<>();
        final List<RawBone> children = new ArrayList<>();

        RawBone(String name, String parentName, float pivotX, float pivotY, float pivotZ,
                float rotX, float rotY, float rotZ) {
            this.name = name;
            this.parentName = parentName;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
        }
    }

    private static final class RawCube {
        final float x;
        final float y;
        final float z;
        final float width;
        final float height;
        final float depth;
        final float pivotX;
        final float pivotY;
        final float pivotZ;
        final float rotX;
        final float rotY;
        final float rotZ;
        final List<RawFace> faces = new ArrayList<>();

        RawCube(float x, float y, float z, float width, float height, float depth,
                float pivotX, float pivotY, float pivotZ,
                float rotX, float rotY, float rotZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
        }
    }

    private record RawFace(Direction direction, float[] positions, float[] uvs) {
    }

    private GeoModelLoader() {
    }
}
