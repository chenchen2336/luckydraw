package cn.blockforge.generated.fabric263wishesfabri.economy;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import cn.blockforge.generated.fabric263wishesfabri.data.PlayerData;
import cn.blockforge.generated.fabric263wishesfabri.data.PlayerStore;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** 服务器没装经济模组时用的内置钱包，数据跟保底计数存在同一个文件里。 */
public final class InternalEconomy implements EconomyBridge {

    @Override
    public String id() {
        return "internal";
    }

    @Override
    public String label() {
        return "内置钱包（未安装经济模组）";
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public CompletableFuture<BigDecimal> balance(UUID player) {
        return CompletableFuture.completedFuture(PlayerStore.get(player).getBalance());
    }

    @Override
    public BigDecimal cachedBalance(UUID player) {
        return PlayerStore.get(player).getBalance();
    }

    @Override
    public CompletableFuture<Boolean> charge(UUID player, BigDecimal amount) {
        PlayerData data = PlayerStore.get(player);
        if (data.getBalance().compareTo(amount) < 0) {
            return CompletableFuture.completedFuture(false);
        }
        data.setBalance(data.getBalance().subtract(amount));
        PlayerStore.save();
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Void> grant(UUID player, BigDecimal amount) {
        PlayerData data = PlayerStore.get(player);
        data.setBalance(data.getBalance().add(amount));
        PlayerStore.save();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String format(BigDecimal amount) {
        LotteryConfig.EconomyCfg cfg = LotteryConfig.get().economy;
        String number = amount.stripTrailingZeros().toPlainString();
        return cfg.symbolBefore ? cfg.symbol + number : number + cfg.symbol;
    }
}
