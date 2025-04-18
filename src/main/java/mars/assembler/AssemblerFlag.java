package mars.assembler;

import mars.Application;

public enum AssemblerFlag {
    DELAYED_BRANCHING("db"),
    BIG_ENDIAN("be"),
    EXTENDED_MODE("pseudo");

    private final String key;

    AssemblerFlag(String key) {
        this.key = key;
    }

    public String getKey() {
        return this.key;
    }

    public boolean isEnabled() {
        return switch (this) {
            case DELAYED_BRANCHING -> Application.getSettings().delayedBranchingEnabled.get();
            case BIG_ENDIAN -> Application.getSettings().useBigEndian.get();
            case EXTENDED_MODE -> Application.getSettings().extendedAssemblerEnabled.get();
        };
    }

    public static AssemblerFlag fromKey(String key) {
        return switch (key.toLowerCase()) {
            case "db" -> DELAYED_BRANCHING;
            case "be" -> BIG_ENDIAN;
            case "pseudo" -> EXTENDED_MODE;
            default -> null;
        };
    }
}
