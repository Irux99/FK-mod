package fr.irux.fk2000.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Local comfort and sound preferences; these never change server damage or guidance. */
public final class ClientEffectsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOCKWAVE_CAMERA, EXHAUST_SMOKE;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_STRENGTH, MOTOR_VOLUME;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SHOCKWAVE_CAMERA = builder.comment("Brief FOV pulse and shake from nearby explosions. Disable for motion sensitivity.")
                .define("shockwaveCamera", true);
        SHOCKWAVE_STRENGTH = builder.defineInRange("shockwaveStrength", .65, 0., 1.);
        EXHAUST_SMOKE = builder.define("exhaustSmoke", true);
        MOTOR_VOLUME = builder.comment("Spatial missile engine volume; the sound follows the missile and fades when it disappears.")
                .defineInRange("missileMotorVolume", .85, 0., 2.);
        SPEC = builder.build();
    }
    private ClientEffectsConfig() {}
}
