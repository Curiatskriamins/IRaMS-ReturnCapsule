package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.block.CapsuleBlock;
import com.curiats.returncapsule.block.CapsulePartBlock;
import com.curiats.returncapsule.block.TransportMarkerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ReturnCapsuleMod.MODID);

    public static final RegistryObject<CapsuleBlock> CAPSULE = BLOCKS.register("capsule",
            () -> new CapsuleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(20.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .dynamicShape()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    public static final RegistryObject<CapsulePartBlock> CAPSULE_PART = BLOCKS.register("capsule_part",
            () -> new CapsulePartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(20.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .noLootTable()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<TransportMarkerBlock> TRANSPORT_MARKER = BLOCKS.register("transport_marker",
            () -> new TransportMarkerBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .strength(-1.0F, 3600000.0F)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    private ModBlocks() {
    }
}
