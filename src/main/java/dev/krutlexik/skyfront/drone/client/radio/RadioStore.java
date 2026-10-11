package dev.krutlexik.skyfront.drone.client.radio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.radio.RadioProfile;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The radio's setup on disk, in {@code config/skyfront-radio.json}. */
final class RadioStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("skyfront-radio.json");
    }

    /** The saved setup, or a fresh one if there is none yet or the file cannot be read. */
    static RadioProfile load() {
        Path path = file();
        if (!Files.exists(path)) {
            return new RadioProfile();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            RadioProfile loaded = GSON.fromJson(reader, RadioProfile.class);
            return repaired(loaded);
        } catch (IOException | JsonParseException exception) {
            DroneMod.LOGGER.warn("Could not read {}, starting the radio from scratch", path, exception);
            return new RadioProfile();
        }
    }

    static void save(RadioProfile profile) {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(profile, writer);
            }
        } catch (IOException exception) {
            DroneMod.LOGGER.error("Could not save the radio setup to {}", path, exception);
        }
    }

    /** Fills in whatever a hand-edited or older file left out. */
    private static RadioProfile repaired(RadioProfile profile) {
        if (profile == null) {
            return new RadioProfile();
        }
        RadioProfile defaults = new RadioProfile();
        if (profile.deviceGuid == null) profile.deviceGuid = "";
        if (profile.deviceName == null) profile.deviceName = "";
        if (profile.roll == null) profile.roll = defaults.roll;
        if (profile.pitch == null) profile.pitch = defaults.pitch;
        if (profile.throttle == null) profile.throttle = defaults.throttle;
        if (profile.yaw == null) profile.yaw = defaults.yaw;
        if (profile.arm == null) profile.arm = defaults.arm;
        if (profile.thermal == null) profile.thermal = defaults.thermal;
        if (profile.camera == null) profile.camera = defaults.camera;
        if (profile.selfDestruct == null) profile.selfDestruct = defaults.selfDestruct;
        if (profile.calibration == null) profile.calibration = defaults.calibration;
        for (RadioProfile.Aux aux : new RadioProfile.Aux[] {profile.arm, profile.thermal, profile.camera,
                profile.selfDestruct}) {
            if (aux.source == null) aux.source = RadioProfile.Source.NONE;
            if (aux.mode == null) aux.mode = RadioProfile.AuxMode.SWITCH;
        }
        return profile;
    }

    private RadioStore() {
    }
}
