package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.registry.ModLootEntries;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.function.Consumer;

public class ModItemLootEntry extends LootPoolSingletonContainer {
    public static final Serializer SERIALIZER = new Serializer();

    private final int minCount;
    private final int maxCount;

    protected ModItemLootEntry(int weight, int quality, LootItemCondition[] conditions,
                               LootItemFunction[] functions, int minCount, int maxCount) {
        super(weight, quality, conditions, functions);
        this.minCount = Math.max(1, minCount);
        this.maxCount = Math.max(this.minCount, maxCount);
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> consumer, LootContext context) {
        if (!Config.ADD_MOD_ITEMS.get()) {
            return;
        }
        ItemStack stack = ModItemPool.random(context.getRandom());
        if (stack.isEmpty()) {
            return;
        }
        int max = Math.min(this.maxCount, stack.getMaxStackSize());
        int min = Math.min(this.minCount, max);
        int count = max <= min ? min : min + context.getRandom().nextInt(max - min + 1);
        stack.setCount(count);
        consumer.accept(stack);
    }

    @Override
    public LootPoolEntryType getType() {
        return ModLootEntries.MOD_ITEM.get();
    }

    public static class Serializer extends LootPoolSingletonContainer.Serializer<ModItemLootEntry> {
        @Override
        public void serializeCustom(JsonObject json, ModItemLootEntry entry, JsonSerializationContext context) {
            super.serializeCustom(json, entry, context);
            json.addProperty("minCount", entry.minCount);
            json.addProperty("maxCount", entry.maxCount);
        }

        @Override
        protected ModItemLootEntry deserialize(JsonObject json, JsonDeserializationContext context,
                                               int weight, int quality, LootItemCondition[] conditions,
                                               LootItemFunction[] functions) {
            int minCount = GsonHelper.getAsInt(json, "minCount", 1);
            int maxCount = GsonHelper.getAsInt(json, "maxCount", minCount);
            return new ModItemLootEntry(weight, quality, conditions, functions, minCount, maxCount);
        }
    }
}
