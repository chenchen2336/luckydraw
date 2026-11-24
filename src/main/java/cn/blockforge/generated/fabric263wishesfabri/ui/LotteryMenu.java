package cn.blockforge.generated.fabric263wishesfabri.ui;

import cn.blockforge.generated.fabric263wishesfabri.game.DrawManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 抽奖界面背后的菜单。它继承原版箱子菜单，所以客户端不需要任何模组；
 * 所有点击都在服务端被拦下来当成按钮用，玩家的物品栏不会被碰到。
 */
public final class LotteryMenu extends ChestMenu {

    public LotteryMenu(int syncId, Player viewer, SimpleContainer container) {
        super(MenuType.GENERIC_9x6, syncId, viewer.getInventory(), container, 6);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        // 先让客户端回到服务的真实内容，避免客户端"预演"拿走界面里的图标
        this.sendAllDataToRemote();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (slotId < 0 || slotId >= LotteryGui.SIZE) return;
        switch (slotId) {
            case LotteryGui.SLOT_SINGLE -> DrawManager.requestDraw(serverPlayer, false);
            case LotteryGui.SLOT_TEN -> DrawManager.requestDraw(serverPlayer, true);
            case LotteryGui.SLOT_PITY -> LotteryGui.sendPity(serverPlayer);
            case LotteryGui.SLOT_WALLET -> LotteryGui.sendBalance(serverPlayer);
            case LotteryGui.SLOT_HELP -> LotteryGui.sendHelp(serverPlayer);
            default -> {
                // 奖池预览格子：只是展示，点了不做事
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }
}
