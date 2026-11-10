package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.nio.file.Path;

/**
 * Registers the bundled titan-fall sound pack, which lives inside this jar under
 * {@code resourcepacks/IRaMS_titan_fall}. Forge does not expose nested pack folders on its
 * own, so they have to be added as a repository source.
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BuiltinSoundPack {
    private static final String FOLDER = "IRaMS_titan_fall";
    private static final String PACK_ID = ReturnCapsuleMod.MODID + ":" + FOLDER;

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(consumer -> {
            Path root = packRoot();
            if (root == null) {
                ReturnCapsuleMod.LOGGER.warn("Bundled sound pack {} is missing from the jar", FOLDER);
                return;
            }
            Pack pack = Pack.readMetaAndCreate(PACK_ID,
                    Component.translatable("resourcepack.returncapsule.titan_fall"),
                    false,
                    id -> new PathPackResources(id, root, true),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (pack != null) {
                consumer.accept(pack);
            }
        });
    }

    private static Path packRoot() {
        IModFileInfo info = ModList.get().getModFileById(ReturnCapsuleMod.MODID);
        return info == null ? null : info.getFile().findResource("resourcepacks", FOLDER);
    }

    private BuiltinSoundPack() {
    }
}
