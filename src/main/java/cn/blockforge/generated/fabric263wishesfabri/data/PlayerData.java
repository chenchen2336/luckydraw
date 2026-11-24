package cn.blockforge.generated.fabric263wishesfabri.data;

import java.math.BigDecimal;

/** 单个玩家在抽奖机里的持久数据：保底计数、总抽数、内置钱包余额。 */
public final class PlayerData {

    private int pity;
    private int totalDraws;
    private BigDecimal balance;

    public PlayerData() {
    }

    public PlayerData(BigDecimal startingBalance) {
        this.balance = startingBalance;
    }

    public int getPity() {
        return pity;
    }

    public void setPity(int pity) {
        this.pity = Math.max(0, pity);
    }

    public int getTotalDraws() {
        return totalDraws;
    }

    public void setTotalDraws(int totalDraws) {
        this.totalDraws = Math.max(0, totalDraws);
    }

    public BigDecimal getBalance() {
        return balance == null ? BigDecimal.ZERO : balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
