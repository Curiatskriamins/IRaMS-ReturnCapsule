package com.curiats.returncapsule.progress;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldProgress extends SavedData {
    private static final String DATA_NAME = "returncapsule_progress";
    private static final String TAG_DRAGON = "DragonDefeated";
    private static final String TAG_WITHER = "WitherDefeated";
    private static final ResourceLocation DRAGON_ADVANCEMENT =
            new ResourceLocation("minecraft", "end/kill_dragon");

    private boolean dragonDefeated;
    private boolean witherDefeated;

    public static WorldProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WorldProgress::load, WorldProgress::new, DATA_NAME);
    }

    public boolean bossDefeated() {
        return this.dragonDefeated || this.witherDefeated;
    }

    public void record(Entity entity) {
        if (entity instanceof EnderDragon) {
            this.dragonDefeated = true;
        } else if (entity instanceof WitherBoss) {
            this.witherDefeated = true;
        } else {
            return;
        }
        this.setDirty();
    }

    public void syncFrom(ServerPlayer player) {
        if (this.dragonDefeated) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement advancement = server.getAdvancements().getAdvancement(DRAGON_ADVANCEMENT);
        if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            this.dragonDefeated = true;
            this.setDirty();
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (!(entity instanceof EnderDragon) && !(entity instanceof WitherBoss)) {
            return;
        }
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return;
        }
        get(server).record(entity);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(TAG_DRAGON, this.dragonDefeated);
        tag.putBoolean(TAG_WITHER, this.witherDefeated);
        return tag;
    }

    private static WorldProgress load(CompoundTag tag) {
        WorldProgress progress = new WorldProgress();
        progress.dragonDefeated = tag.getBoolean(TAG_DRAGON);
        progress.witherDefeated = tag.getBoolean(TAG_WITHER);
        return progress;
    }

    private WorldProgress() {
    }
}
