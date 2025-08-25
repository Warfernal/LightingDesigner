package com.phoenixcorp.overlay;

public final class FakeOcrReader implements OcrReader {
    private int t = 0;
    private OcrReader.ResourceType type = ResourceType.MANA;

    @Override
    public Snapshot read() {
        t = (t + 1) % 200;
        double hpPct  = 0.6 + 0.4 * Math.sin(t / 20.0);
        double resPct = 0.2 + 0.8 * Math.abs(Math.sin(t / 15.0));

        if (t % 120 == 0) {
            type = switch (type) {
                case MANA -> ResourceType.RAGE;
                case RAGE -> ResourceType.ENERGY;
                case ENERGY -> ResourceType.RUNIC_POWER;
                default -> ResourceType.MANA;
            };
        }

        int hpMax = 1000, resMax = 1000;
        int hpCur = (int) Math.round(hpPct * hpMax);
        int resCur = (int) Math.round(resPct * resMax);
        return new Snapshot(hpCur, hpMax, resCur, resMax, type);
    }
}
