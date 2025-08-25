package com.phoenixcorp.overlay;

public interface OcrReader {

    final class Snapshot {
        public final int hpCur, hpMax;
        public final int resCur, resMax;
        public final ResourceType type;
        public Snapshot(int hpCur, int hpMax, int resCur, int resMax, ResourceType type) {
            this.hpCur = hpCur; this.hpMax = hpMax; this.resCur = resCur; this.resMax = resMax;
            this.type = type == null ? ResourceType.UNKNOWN : type;
        }
        @Override public String toString() {
            return "Snapshot{hp=" + hpCur + "/" + hpMax + ", res=" + resCur + "/" + resMax + ", type=" + type + "}";
        }
    }

    enum ResourceType { MANA, RAGE, ENERGY, RUNIC_POWER, FOCUS, MAELSTROM, FURY, INSANITY, UNKNOWN }

    /** Renvoie null si la frame OCR n'est pas fiable (jamais d’exception) */
    Snapshot read();
}
