package cn.blockforge.generated.fabric263wishesfabri.economy;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 经济接口。抽奖机只认这一层：扣费和发奖都走同一个账户。
 * 具体实现可以是 Savs Common Economy，也可以是没装经济模组时的内置钱包。
 */
public interface EconomyBridge {

    /** 内部标识，写进日志用。 */
    String id();

    /** 展示给玩家看的名字。 */
    String label();

    /** 查询余额（可能是异步的）。 */
    CompletableFuture<BigDecimal> balance(UUID player);

    /** 从缓存里读余额，读不到返回 null（只用于界面展示）。 */
    BigDecimal cachedBalance(UUID player);

    /** 扣钱，返回 true 表示扣款成功；余额不足返回 false。 */
    CompletableFuture<Boolean> charge(UUID player, BigDecimal amount);

    /** 发钱。 */
    CompletableFuture<Void> grant(UUID player, BigDecimal amount);

    /** 金额显示格式，例如 "$100" 或 "100币"。 */
    String format(BigDecimal amount);

    /** 是否是模组内置的钱包。 */
    default boolean isInternal() {
        return false;
    }
}
