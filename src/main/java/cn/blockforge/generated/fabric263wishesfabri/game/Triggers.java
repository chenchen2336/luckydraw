package cn.blockforge.generated.fabric263wishesfabri.game;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 把配置里的 "minecraft:lodestone" / "minecraft:clock" 解析成实际方块和物品。 */
public final class Triggers {

    private static Block block = Blocks.LODESTONE;
    private static Item item;

    private Triggers() {
    }

    public static void refresh() {
        LotteryConfig.Machine machine = LotteryConfig.get().machine;
        block = resolveBlock(machine.block);
        item = LotteryConfig.item(machine.item);
    }

    public static boolean isMachineBlock(BlockState state) {
        return block != null && state != null && state.getBlock() == block;
    }

    public static boolean isMachineItem(ItemStack stack) {
        return item != null && stack != null && !stack.isEmpty() && stack.getItem() == item;
    }

    public static String blockName() {
        return block == null ? "（未设置）" : BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    public static String itemName() {
        return item == null ? "（未设置）" : BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Block resolveBlock(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        try {
            Identifier id = text.indexOf(':') >= 0
                    ? Identifier.parse(text)
                    : Identifier.fromNamespaceAndPath("minecraft", text);
            return BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
