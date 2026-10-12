package dev.krutlexik.skyfront.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage side of the launch terminal: the list of bound Shahed-136 drones with their assigned numbers,
 * planned routes and per-drone settings.
 */
public class ShahedTerminalBlockEntity extends BlockEntity {
    public static class Entry {
        public UUID uuid;
        public int number;
        public int altitude;
        /** true = long (high) dive from ~500 blocks out; false = close (low) dive from ~150. */
        public boolean farDive = true;
        /** Route as flattened (x,z) pairs; the last pair is the strike target. */
        public int[] route = new int[0];

        public Entry(UUID uuid, int number, int altitude) {
            this.uuid = uuid;
            this.number = number;
            this.altitude = altitude;
        }

        public int waypointCount() {
            return this.route.length / 2;
        }

        public int waypointX(int index) {
            return this.route[index * 2];
        }

        public int waypointZ(int index) {
            return this.route[index * 2 + 1];
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int nextNumber = 1;

    public ShahedTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAHED_TERMINAL.get(), pos, state);
    }

    public List<Entry> getEntries() {
        return this.entries;
    }

    @Nullable
    public Entry findByUuid(UUID uuid) {
        for (Entry entry : this.entries) {
            if (entry.uuid.equals(uuid)) {
                return entry;
            }
        }
        return null;
    }

    /** Binds a drone and assigns it the next number. */
    public Entry bind(UUID uuid) {
        Entry entry = new Entry(uuid, this.nextNumber++, 150);
        this.entries.add(entry);
        this.setChanged();
        return entry;
    }

    public boolean unbind(UUID uuid) {
        boolean removed = this.entries.removeIf(e -> e.uuid.equals(uuid));
        if (removed) {
            this.setChanged();
        }
        return removed;
    }

    /**
     * The laptop stands half a block above the case, so the post is drawn while any of it is in view (on
     * NeoForge this was ShahedTerminalRenderer#getRenderBoundingBox; Forge 1.20.1 asks the block entity).
     */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.getBlockPos()).expandTowards(0.0D, 0.75D, 0.0D);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("NextNumber", this.nextNumber);
        ListTag list = new ListTag();
        for (Entry entry : this.entries) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Uuid", entry.uuid);
            e.putInt("Number", entry.number);
            e.putInt("Altitude", entry.altitude);
            e.putBoolean("FarDive", entry.farDive);
            e.putIntArray("Route", entry.route);
            list.add(e);
        }
        tag.put("Shaheds", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.nextNumber = Math.max(1, tag.getInt("NextNumber"));
        this.entries.clear();
        ListTag list = tag.getList("Shaheds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            Entry entry = new Entry(e.getUUID("Uuid"), e.getInt("Number"), e.getInt("Altitude"));
            entry.farDive = !e.contains("FarDive") || e.getBoolean("FarDive");
            int[] route = e.getIntArray("Route");
            entry.route = route.length % 2 == 0 ? route : new int[0];
            this.entries.add(entry);
        }
    }
}
