package cn.blockforge.generated.fabric263wishesfabri.ui;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import cn.blockforge.generated.fabric263wishesfabri.economy.Economies;
import cn.blockforge.generated.fabric263wishesfabri.economy.EconomyBridge;
import cn.blockforge.generated.fabric263wishesfabri.game.DrawManager;
import cn.blockforge.generated.fabric263wishesfabri.game.PrizeText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖界面。用原版箱子界面画出来（6 行 54 格），
 * 这样没装模组的客户端也能正常显示，不会出现紫黑方块。
 */
public final class LotteryGui {

    public static final int SIZE = 54;
    public static final int SLOT_SINGLE = 20;
    public static final int SLOT_PITY = 22;
    public static final int SLOT_TEN = 24;
    public static final int SLOT_WALLET = 31;
    public static final int SLOT_HELP = 40;

    private LotteryGui() {
    }

    public static void open(ServerPlayer player) {
        LotteryConfig cfg = LotteryConfig.get();
        if (!cfg.enabled) {
            DrawManager.message(player, "抽奖机现在关着，问一下管理员吧。");
            return;
        }
        if (player.containerMenu instanceof LotteryMenu) {
            return;
        }
        SimpleContainer container = build(player);
        Component title = Component.literal(cfg.ui.title).withStyle(ChatFormatting.GOLD);
        player.openMenu(new SimpleMenuProvider(
                (syncId, inventory, viewer) -> new LotteryMenu(syncId, viewer, container), title));
    }

    private static SimpleContainer build(ServerPlayer player) {
        LotteryConfig cfg = LotteryConfig.get();
        SimpleContainer container = new SimpleContainer(SIZE);

        ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.black());
        filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < SIZE; i++) {
            container.setItem(i, filler.copy());
        }

        // 第一行：单抽奖池预览；第二行：十连奖池预览
        int singleTotal = fillPreview(container, cfg.pools.single, 0);
        int tenTotal = fillPreview(container, cfg.pools.ten, 9);

        EconomyBridge economy = Economies.get();
        PlayerDataView view = PlayerDataView.of(player);

        container.setItem(SLOT_SINGLE, button(Items.EMERALD, "单抽",
                List.of(Component.literal("价格 " + economy.format(cfg.price.single)).withStyle(ChatFormatting.GREEN),
                        Component.literal("从第一行奖池抽 1 次（共 " + singleTotal + " 种奖品）").withStyle(ChatFormatting.GRAY),
                        Component.literal("点击开始抽奖").withStyle(ChatFormatting.YELLOW))));

        List<Component> tenLore = new ArrayList<>();
        tenLore.add(Component.literal("价格 " + economy.format(cfg.price.ten)).withStyle(ChatFormatting.GREEN));
        tenLore.add(Component.literal("从第二行奖池抽 10 次（共 " + tenTotal + " 种奖品）").withStyle(ChatFormatting.GRAY));
        BigDecimal tenTimes = cfg.price.single.multiply(BigDecimal.TEN);
        if (cfg.price.ten.compareTo(tenTimes) < 0) {
            tenLore.add(Component.literal("比单抽十次省 " + economy.format(tenTimes.subtract(cfg.price.ten)))
                    .withStyle(ChatFormatting.GOLD));
        }
        tenLore.add(Component.literal("点击开始抽奖").withStyle(ChatFormatting.YELLOW));
        container.setItem(SLOT_TEN, button(Items.DIAMOND, "十连抽", tenLore));

        List<Component> pityLore = new ArrayList<>();
        if (cfg.pity.enabled) {
            pityLore.add(Component.literal("已累计 " + view.pity + "/" + cfg.pity.threshold + " 抽")
                    .withStyle(ChatFormatting.AQUA));
            pityLore.add(Component.literal("再 " + Math.max(0, cfg.pity.threshold - view.pity) + " 抽必出稀有")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            pityLore.add(Component.literal("本服没有开启保底").withStyle(ChatFormatting.GRAY));
        }
        container.setItem(SLOT_PITY, button(Items.NETHER_STAR, "保底进度", pityLore));

        List<Component> walletLore = new ArrayList<>();
        BigDecimal cached = economy.cachedBalance(player.getUUID());
        if (cached != null) {
            walletLore.add(Component.literal("余额 " + economy.format(cached)).withStyle(ChatFormatting.GOLD));
            walletLore.add(Component.literal("点击查询最新余额").withStyle(ChatFormatting.GRAY));
        } else {
            walletLore.add(Component.literal("点击查询余额").withStyle(ChatFormatting.GRAY));
        }
        walletLore.add(Component.literal("来源：" + economy.label()).withStyle(ChatFormatting.DARK_GRAY));
        container.setItem(SLOT_WALLET, button(Items.GOLD_INGOT, "我的钱包", walletLore));

        container.setItem(SLOT_HELP, button(Items.BOOK, "玩法说明",
                List.of(Component.literal("当前累计抽了 " + view.totalDraws + " 次").withStyle(ChatFormatting.GRAY),
                        Component.literal("点击在聊天栏看说明").withStyle(ChatFormatting.YELLOW))));

        return container;
    }

    private static int fillPreview(SimpleContainer container, List<LotteryConfig.Prize> pool, int offset) {
        if (pool == null || pool.isEmpty()) return 0;
        double total = PrizeText.totalWeight(pool);
        List<LotteryConfig.Prize> sorted = new ArrayList<>(pool);
        sorted.sort((a, b) -> {
            if (a.rare != b.rare) return a.rare ? -1 : 1;
            return Double.compare(a.weight, b.weight);
        });
        int slot = offset;
        int shown = 0;
        for (LotteryConfig.Prize prize : sorted) {
            if (shown >= 9) break;
            container.setItem(slot++, tile(prize, total));
            shown++;
        }
        return pool.size();
    }

    private static ItemStack tile(LotteryConfig.Prize prize, double totalWeight) {
        ItemStack stack = PrizeText.icon(prize);
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.PAPER);
        }
        if (prize.name != null && !prize.name.isBlank()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(prize.name)
                    .withStyle(prize.rare ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE));
        }
        PrizeText.applyLore(stack, PrizeText.lore(prize, totalWeight));
        return stack;
    }

    private static ItemStack button(Item item, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD));
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    // ---------- 聊天栏里的信息 ----------

    public static void sendHelp(ServerPlayer player) {
        LotteryConfig cfg = LotteryConfig.get();
        EconomyBridge economy = Economies.get();
        player.sendSystemMessage(Component.literal("━━━━━━ 抽奖机玩法 ━━━━━━").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("· 右键抽奖机方块，或手持抽奖物品右键，打开抽奖界面")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("· 单抽 " + economy.format(cfg.price.single)
                + "，十连 " + economy.format(cfg.price.ten) + "，从你的经济账户直接扣")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("· 第一行是单抽奖池，第二行是十连奖池，鼠标悬停能看概率")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("· 每 " + cfg.pity.threshold + " 抽必出一个稀有奖励，进度跨重登保存")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("· 中奖后物品直接进背包，命令类奖品也会自动发给你，不用你自己有管理员权限")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("· 也输入 /lottery open 随时打开界面").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("· 管理员：奖池/价格/概率在 config/luckydraw/lottery.json，改完 /lottery reload")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void sendPity(ServerPlayer player) {
        LotteryConfig cfg = LotteryConfig.get();
        PlayerDataView view = PlayerDataView.of(player);
        if (!cfg.pity.enabled) {
            DrawManager.message(player, "本服没有开启保底机制。");
            return;
        }
        DrawManager.message(player, "保底进度 " + view.pity + "/" + cfg.pity.threshold
                + "，再 " + Math.max(0, cfg.pity.threshold - view.pity)
                + " 抽必出稀有；你一共抽过 " + view.totalDraws + " 次。");
    }

    public static void sendBalance(ServerPlayer player) {
        EconomyBridge economy = Economies.get();
        MinecraftServer server = player.level().getServer();
        economy.balance(player.getUUID()).thenAccept(balance -> server.executeIfPossible(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
            if (online == null) return;
            BigDecimal amount = balance == null ? BigDecimal.ZERO : balance;
            DrawManager.message(online, "余额 " + economy.format(amount) + "（" + economy.label() + "）");
        }));
    }

    /** 打开界面时读一次保底计数和总抽数，避免重复查表。 */
    private static final class PlayerDataView {
        final int pity;
        final int totalDraws;

        private PlayerDataView(int pity, int totalDraws) {
            this.pity = pity;
            this.totalDraws = totalDraws;
        }

        static PlayerDataView of(ServerPlayer player) {
            var data = cn.blockforge.generated.fabric263wishesfabri.data.PlayerStore.get(player.getUUID());
            return new PlayerDataView(data.getPity(), data.getTotalDraws());
        }
    }
}
