package cn.blockforge.generated.fabric263wishesfabri.command;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import cn.blockforge.generated.fabric263wishesfabri.data.PlayerData;
import cn.blockforge.generated.fabric263wishesfabri.data.PlayerStore;
import cn.blockforge.generated.fabric263wishesfabri.economy.Economies;
import cn.blockforge.generated.fabric263wishesfabri.economy.EconomyBridge;
import cn.blockforge.generated.fabric263wishesfabri.game.PrizeCommands;
import cn.blockforge.generated.fabric263wishesfabri.game.PrizeText;
import cn.blockforge.generated.fabric263wishesfabri.game.Triggers;
import cn.blockforge.generated.fabric263wishesfabri.ui.LotteryGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** /lottery 命令树。 */
public final class LotteryCommand {

    private LotteryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lottery")
                .executes(context -> openGui(context.getSource()))
                .then(Commands.literal("open").executes(context -> openGui(context.getSource())))
                .then(Commands.literal("help").executes(context -> {
                    LotteryGui.sendHelp(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("info").executes(context -> info(context.getSource())))
                .then(Commands.literal("pool").executes(context -> pool(context.getSource())))
                .then(Commands.literal("pity")
                        .executes(context -> {
                            LotteryGui.sendPity(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> {
                                    LotteryGui.sendPity(EntityArgument.getPlayer(context, "player"));
                                    return 1;
                                })))
                .then(Commands.literal("balance").executes(context -> {
                    LotteryGui.sendBalance(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                        .executes(context -> adjust(context, true)))))
                .then(Commands.literal("take")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                        .executes(context -> adjust(context, false))))));
    }

    private static int openGui(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LotteryGui.open(player);
        return 1;
    }

    private static int info(CommandSourceStack source) {
        LotteryConfig cfg = LotteryConfig.get();
        EconomyBridge economy = Economies.get();
        reply(source, Component.literal("━━━━━━ 抽奖机状态 ━━━━━━").withStyle(ChatFormatting.GOLD));
        reply(source, line("经济来源", economy.label()));
        reply(source, line("单抽价格", economy.format(cfg.price.single)));
        reply(source, line("十连价格", economy.format(cfg.price.ten)));
        reply(source, line("单抽奖池", cfg.pools.single.size() + " 种奖品"));
        reply(source, line("十连奖池", cfg.pools.ten.size() + " 种奖品"));
        reply(source, line("保底", cfg.pity.enabled ? ("每 " + cfg.pity.threshold + " 抽必出稀有") : "未开启"));
        reply(source, line("触发方块", Triggers.blockName()));
        reply(source, line("触发物品", Triggers.itemName()));
        reply(source, line("命令奖品身份", PrizeCommands.label(cfg.prizeCommands.runAs)));
        reply(source, line("动画时长", cfg.effects.animationTicks + " tick  |  粒子" + onOff(cfg.effects.particles)
                + "  音效" + onOff(cfg.effects.sounds)));
        return 1;
    }

    private static int pool(CommandSourceStack source) {
        LotteryConfig cfg = LotteryConfig.get();
        EconomyBridge economy = Economies.get();
        reply(source, Component.literal("━━━━━━ 单抽奖池 ━━━━━━").withStyle(ChatFormatting.GOLD));
        listPool(source, cfg.pools.single, economy);
        reply(source, Component.literal("━━━━━━ 十连奖池 ━━━━━━").withStyle(ChatFormatting.GOLD));
        listPool(source, cfg.pools.ten, economy);
        return 1;
    }

    private static void listPool(CommandSourceStack source, List<LotteryConfig.Prize> pool, EconomyBridge economy) {
        if (pool == null || pool.isEmpty()) {
            reply(source, Component.literal("（空）").withStyle(ChatFormatting.GRAY));
            return;
        }
        double total = PrizeText.totalWeight(pool);
        for (LotteryConfig.Prize prize : pool) {
            double chance = total <= 0 ? 0.0D : prize.weight / total * 100.0D;
            Component line = Component.empty()
                    .append(Component.literal(prize.rare ? "★ " : "· ")
                            .withStyle(prize.rare ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY))
                    .append(PrizeText.name(prize).copy().withStyle(prize.rare ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE))
                    .append(Component.literal("  " + String.format(Locale.ROOT, "%.1f%%", chance))
                            .withStyle(ChatFormatting.AQUA));
            reply(source, line);
        }
    }

    private static int reload(CommandSourceStack source) {
        LotteryConfig.reload();
        Triggers.refresh();
        Economies.init();
        LotteryConfig cfg = LotteryConfig.get();
        reply(source, Component.literal("配置已重新读取：单抽 " + Economies.get().format(cfg.price.single)
                + " / 十连 " + Economies.get().format(cfg.price.ten)
                + "，奖池 " + cfg.pools.single.size() + " + " + cfg.pools.ten.size()
                + " 项，经济来源 " + Economies.get().label()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int adjust(CommandContext<CommandSourceStack> context, boolean give) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        double raw = DoubleArgumentType.getDouble(context, "amount");
        EconomyBridge economy = Economies.get();
        if (!economy.isInternal()) {
            source.sendFailure(Component.literal("当前用的是 " + economy.label() + "，请用它自己的命令给钱（例如 /givemoney）。"));
            return 0;
        }
        BigDecimal amount = BigDecimal.valueOf(raw);
        PlayerData data = PlayerStore.get(target.getUUID());
        if (!give && data.getBalance().compareTo(amount) < 0) {
            source.sendFailure(Component.literal(target.getName().getString() + " 只有 "
                    + economy.format(data.getBalance()) + "，扣不了这么多。"));
            return 0;
        }
        data.setBalance(give ? data.getBalance().add(amount) : data.getBalance().subtract(amount));
        PlayerStore.save();
        reply(source, Component.literal((give ? "已给 " : "已从 ")
                + target.getName().getString() + (give ? " 加上 " : " 扣除 ")
                + economy.format(amount) + "，现在有 " + economy.format(data.getBalance()))
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static void reply(CommandSourceStack source, Component component) {
        source.sendSuccess(() -> component, false);
    }

    private static Component line(String key, String value) {
        return Component.empty()
                .append(Component.literal(key + "：").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static String onOff(boolean value) {
        return value ? "开" : "关";
    }
}
