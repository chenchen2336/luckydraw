package cn.blockforge.generated.fabric263wishesfabri;

import cn.blockforge.generated.fabric263wishesfabri.command.LotteryCommand;
import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import cn.blockforge.generated.fabric263wishesfabri.data.PlayerStore;
import cn.blockforge.generated.fabric263wishesfabri.economy.Economies;
import cn.blockforge.generated.fabric263wishesfabri.game.DrawManager;
import cn.blockforge.generated.fabric263wishesfabri.game.Triggers;
import cn.blockforge.generated.fabric263wishesfabri.ui.LotteryGui;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.BlockEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 幸运抽奖机：26.3 Fabric 服务器端抽奖模组。
 * <p>
 * 只装在服务器上就够了——没有新增方块、物品、音效或界面资源，
 * 一切表现都用原版粒子和音效，界面用原版箱子界面，所以纯净客户端也能玩。
 */
public final class LotteryMod implements ModInitializer {

    public static final String MOD_ID = "fabric_26_3_wishes_fabri";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LotteryConfig cfg = LotteryConfig.get();
        LOGGER.info("[抽奖机] 已加载：单抽 {} / 十连 {}，奖池 {} + {} 项，保底 {} 抽",
                cfg.price.single.toPlainString(), cfg.price.ten.toPlainString(),
                cfg.pools.single.size(), cfg.pools.ten.size(),
                cfg.pity.enabled ? cfg.pity.threshold : 0);
        Triggers.refresh();

        // 右键抽奖机方块
        BlockEvents.USE_ITEM_ON.register((stack, state, level, pos, player, hand, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!Triggers.isMachineBlock(state)) return InteractionResult.PASS;
            if (LotteryConfig.get().machine.requireSneak && !serverPlayer.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            LotteryGui.open(serverPlayer);
            return InteractionResult.SUCCESS;
        });

        // 手持抽奖物品右键
        ItemEvents.USE.register((level, player, hand) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!Triggers.isMachineItem(player.getItemInHand(hand))) return InteractionResult.PASS;
            if (LotteryConfig.get().machine.requireSneak && !serverPlayer.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            LotteryGui.open(serverPlayer);
            return InteractionResult.SUCCESS;
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, selection) -> LotteryCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PlayerStore.init(server);
            Economies.init();
            LOGGER.info("[抽奖机] 经济来源：{}", Economies.get().label());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DrawManager.flush(server);
            PlayerStore.save();
        });
        ServerTickEvents.END_SERVER_TICK.register(DrawManager::tick);
    }
}
