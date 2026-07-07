package com.soymods.oreveil.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum XrayProfile {
    VANILLA_FRIENDLY("vanilla-friendly", "Vanilla Friendly", 0.55D, 1.10D, 0.65D, 0.90D, 0.12D),
    BALANCED("balanced", "Balanced", 1.0D, 1.0D, 1.0D, 1.0D, 0.16D),
    AGGRESSIVE("aggressive", "Aggressive", 1.55D, 1.30D, 0.75D, 1.25D, 0.14D),
    PERFORMANCE("performance", "Performance", 0.35D, 1.20D, 0.50D, 0.80D, 0.10D);

    private final String configName;
    private final String displayName;
    private final double saltDensityMultiplier;
    private final double commonOreWeightMultiplier;
    private final double rareOreWeightMultiplier;
    private final double veinSizeMultiplier;
    private final double maxRareOreShare;

    XrayProfile(
        String configName,
        String displayName,
        double saltDensityMultiplier,
        double commonOreWeightMultiplier,
        double rareOreWeightMultiplier,
        double veinSizeMultiplier,
        double maxRareOreShare
    ) {
        this.configName = configName;
        this.displayName = displayName;
        this.saltDensityMultiplier = saltDensityMultiplier;
        this.commonOreWeightMultiplier = commonOreWeightMultiplier;
        this.rareOreWeightMultiplier = rareOreWeightMultiplier;
        this.veinSizeMultiplier = veinSizeMultiplier;
        this.maxRareOreShare = maxRareOreShare;
    }

    public String configName() {
        return configName;
    }

    public String displayName() {
        return displayName;
    }

    public double saltDensityMultiplier() {
        return saltDensityMultiplier;
    }

    public double commonOreWeightMultiplier() {
        return commonOreWeightMultiplier;
    }

    public double rareOreWeightMultiplier() {
        return rareOreWeightMultiplier;
    }

    public double veinSizeMultiplier() {
        return veinSizeMultiplier;
    }

    public int maxRareOreBlocks(int effectiveSaltBudget) {
        return Math.max(1, (int) Math.floor(effectiveSaltBudget * maxRareOreShare));
    }

    public int effectiveWeight(int baseWeight, OreRarity rarity) {
        double multiplier = switch (rarity) {
            case COMMON -> commonOreWeightMultiplier;
            case NORMAL -> 1.0D;
            case RARE -> rareOreWeightMultiplier;
        };
        return Math.max(1, (int) Math.round(baseWeight * multiplier));
    }

    public int effectiveVeinSize(int baseVeinSize) {
        return Math.max(1, (int) Math.round(baseVeinSize * veinSizeMultiplier));
    }

    public int effectiveSaltBudget(int configuredSaltDensity) {
        return Math.max(1, (int) Math.round(configuredSaltDensity * saltDensityMultiplier));
    }

    public static XrayProfile fromConfig(String raw) {
        XrayProfile parsed = parse(raw);
        return parsed == null ? BALANCED : parsed;
    }

    public static XrayProfile parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (XrayProfile profile : values()) {
            if (profile.configName.equals(normalized) || profile.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(normalized)) {
                return profile;
            }
        }
        return null;
    }

    public static List<String> configNames() {
        return Arrays.stream(values()).map(XrayProfile::configName).toList();
    }

    public enum OreRarity {
        COMMON,
        NORMAL,
        RARE
    }
}
