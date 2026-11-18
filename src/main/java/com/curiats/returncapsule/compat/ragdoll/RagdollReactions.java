package com.curiats.returncapsule.compat.ragdoll;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.raiiiden.ragdollified.api.RagdollHandle;
import com.raiiiden.ragdollified.api.RagdollState;
import com.raiiiden.ragdollified.api.RagdollifiedApi;
import com.raiiiden.ragdollified.api.SpawnOptions;
import net.minecraftforge.fml.ModList;

public final class RagdollReactions {

    public static final String MOD_ID = "ragdollified";

    private static final java.util.Map<Integer, RagdollHandle> HANDLES = new java.util.concurrent.ConcurrentHashMap<>();

    private RagdollReactions() {
    }

    public static boolean available() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void sync(NeutralHumanoid humanoid) {
        if (!available()) {
            return;
        }
        boolean downed = humanoid.isDownedSynced();
        boolean active = HANDLES.containsKey(humanoid.getId());
        if (downed && !active) {
            start(humanoid);
        } else if (!downed && active) {
            end(humanoid);
        } else if (downed && active && humanoid.tickCount % 5 == 0) {
            followTorso(humanoid);
        }
    }

    private static void followTorso(NeutralHumanoid humanoid) {
        RagdollHandle handle = HANDLES.get(humanoid.getId());
        if (handle == null || !handle.exists()) {
            return;
        }
        RagdollState state = handle.getState().orElse(null);
        if (state != null) {
            com.curiats.returncapsule.network.ModNetwork.sendRagdollEnd(humanoid.getId(), state.torsoPosition());
        }
    }

    public static boolean start(NeutralHumanoid humanoid) {
        if (!available() || !humanoid.level().isClientSide || HANDLES.containsKey(humanoid.getId())) {
            return false;
        }
        try {
            RagdollHandle handle = RagdollifiedApi.spawn(humanoid, SpawnOptions.builder()
                    .hideEntity(true)
                    .persistent(false)
                    .build());
            if (handle == null) {
                ReturnCapsuleMod.LOGGER.warn("ragdoll spawn returned no handle for {}", humanoid.getName().getString());
                return false;
            }
            HANDLES.put(humanoid.getId(), handle);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.warn("ragdoll spawn failed, the reaction needs fixing: {}", exception.toString());
            return false;
        }
    }

    public static void end(NeutralHumanoid humanoid) {
        if (!available() || !humanoid.level().isClientSide) {
            return;
        }
        RagdollHandle handle = HANDLES.remove(humanoid.getId());
        try {
            if (handle != null && handle.exists()) {
                RagdollState state = handle.getState().orElse(null);
                if (state != null) {
                    com.curiats.returncapsule.network.ModNetwork.sendRagdollEnd(humanoid.getId(),
                            state.torsoPosition());
                }
                RagdollifiedApi.remove(handle.entityId());
                com.curiats.returncapsule.client.HumanoidImpactDetector.reset(humanoid.getId());
            }
            RagdollifiedApi.setEntityHidden(humanoid.getId(), false);
        } catch (RuntimeException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.debug("ragdoll end failed: {}", exception.toString());
        }
    }
}
