package cn.blockforge.generated.fabric263wishesfabri.game;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import cn.blockforge.generated.fabric263wishesfabri.economy.Economies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 把奖品翻译成玩家看得懂的名字、图标和说明文字。 */
public final class PrizeText {

    private PrizeText() {
    }

    public static double totalWeight(List<LotteryConfig.Prize> pool) {
        double total = 0.0D;
        if (pool == null) return total;
        for (LotteryConfig.Prize prize : pool) {
            if (prize != null && prize.weight > 0) total += prize.weight;
        }
        return total;
    }

    /** 奖品对应的物品堆（钱和纯命令类奖品返回空栈）。 */
    public static ItemStack itemStack(LotteryConfig.Prize prize) {
        if (prize == null) return ItemStack.EMPTY;
        Item item = prize.resolveItem();
        if (item == null) return ItemStack.EMPTY;
        int count = Math.max(1, Math.min(prize.count, 64));
        return new ItemStack(item, count);
    }

    /** 界面里显示用的图标：物品奖品用物品本身，纯钱奖品用金锭代替。 */
    public static ItemStack icon(LotteryConfig.Prize prize) {
        ItemStack stack = itemStack(prize);
        if (!stack.isEmpty()) return stack;
        if (prize != null && prize.money != null && prize.money.signum() > 0) {
            ItemStack coin = new ItemStack(Items.GOLD_INGOT);
            coin.set(DataComponents.CUSTOM_NAME, Component.literal(Economies.get().format(prize.money))
                    .withStyle(ChatFormatting.GOLD));
            return coin;
        }
        return new ItemStack(Items.PAPER);
    }

    public static Component name(LotteryConfig.Prize prize) {
        if (prize.name != null && !prize.name.isBlank()) {
            return Component.literal(prize.name);
        }
        if (prize.money != null && prize.money.signum() > 0) {
            return Component.literal(Economies.get().format(prize.money));
        }
        ItemStack stack = itemStack(prize);
        if (!stack.isEmpty()) return stack.getHoverName();
        return Component.literal("神秘奖励");
    }

    /** 名字 + 数量，例如 "钻石 ×2"。 */
    public static Component nameWithCount(LotteryConfig.Prize prize) {
        Component base = name(prize).copy().withStyle(prize.rare ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE);
        if (itemStack(prize).isEmpty()) return base;
        return Component.empty().append(base)
                .append(Component.literal(" ×" + Math.max(1, Math.min(prize.count, 64))).withStyle(ChatFormatting.GRAY));
    }

    /** 界面里的说明文字：稀有度 + 概率。 */
    public static List<Component> lore(LotteryConfig.Prize prize, double totalWeight) {
        List<Component> lines = new ArrayList<>();
        lines.add(prize.rare
                ? Component.literal("稀有奖励").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.literal("普通奖励").withStyle(ChatFormatting.GRAY));
        if (totalWeight > 0) {
            double chance = prize.weight / totalWeight * 100.0D;
            lines.add(Component.literal(String.format(Locale.ROOT, "概率 %.1f%%", chance))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (prize.money != null && prize.money.signum() > 0 && itemStack(prize).isEmpty()) {
            lines.add(Component.literal("中奖后直接进钱包").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (prize.commands != null && !prize.commands.isEmpty()) {
            lines.add(Component.literal("附带服务器特殊奖励").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    public static void applyLore(ItemStack stack, List<Component> lines) {
        if (lines == null || lines.isEmpty()) return;
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    public static Component formatMoney(BigDecimal amount) {
        return Component.literal(Economies.get().format(amount)).withStyle(ChatFormatting.GOLD);
    }
}
