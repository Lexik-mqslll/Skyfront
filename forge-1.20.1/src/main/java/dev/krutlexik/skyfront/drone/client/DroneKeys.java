package dev.krutlexik.skyfront.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

/** Key bindings that only mean anything while a drone is being flown. */
public final class DroneKeys {
    public static final String CATEGORY = "key.categories.sfw.drone";

    /** The arming switch on the interceptor: motors live, motors dead. */
    public static final KeyMapping ARM = new KeyMapping(
            "key.sfw.drone_arm",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_TAB,
            CATEGORY);

    /** The thermal camera, on the drones that carry one: heat on, heat off. */
    public static final KeyMapping THERMAL = new KeyMapping(
            "key.sfw.drone_thermal",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_T,
            CATEGORY);

    /** Opens the radio setup. */
    public static final KeyMapping RADIO_SETTINGS = new KeyMapping(
            "key.sfw.radio_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    /** Self-destruct on the interceptors and the Molniya: the warhead goes off where the drone is. */
    public static final KeyMapping SELF_DESTRUCT = new KeyMapping(
            "key.sfw.drone_self_destruct",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    private DroneKeys() {
    }
}
