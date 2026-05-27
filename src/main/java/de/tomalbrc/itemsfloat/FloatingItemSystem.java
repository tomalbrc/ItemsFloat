package de.tomalbrc.itemsfloat;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemPhysicsComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemPhysicsSystem;
import com.hypixel.hytale.server.core.modules.entity.item.ItemPrePhysicsSystem;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.system.StandardPhysicsTickSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.Set;

public class FloatingItemSystem extends EntityTickingSystem<EntityStore> {
    private final Query<EntityStore> query;

    public FloatingItemSystem() {
        this.query = Query.and(TransformComponent.getComponentType(), Velocity.getComponentType(), BoundingBox.getComponentType(), ItemComponent.getComponentType());
    }

    @Nonnull
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    public int isInFluid(@Nonnull Store<EntityStore> store, int x, int y, int z) {
        World world = store.getExternalData().getWorld();
        byte level = 0;
        ChunkStore chunkStore = world.getChunkStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        Ref<ChunkStore> columnRef = chunkStore.getChunkReference(chunkIndex);
        if (columnRef != null && columnRef.isValid()) {
            ChunkColumn column = chunkStore.getStore().getComponent(columnRef, ChunkColumn.getComponentType());
            if (column != null) {
                int sectionY = y >> 5;
                Ref<ChunkStore>[] sections = column.getSections();
                if (sectionY >= 0 && sectionY < sections.length) {
                    Ref<ChunkStore> sectionRef = sections[sectionY];
                    if (sectionRef != null && sectionRef.isValid()) {
                        FluidSection fluidSection = chunkStore.getStore().getComponent(sectionRef, FluidSection.getComponentType());
                        if (fluidSection != null) {
                            level = fluidSection.getFluidLevel(x & 31, y & 31, z & 31);
                        }
                    }
                }
            }
        }

        return level;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        ItemPhysicsComponent itemComponent = chunk.getComponent(index, ItemPhysicsComponent.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d pos = transform.getPosition();
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);
        int z = (int) Math.floor(pos.z);

        int fluidLevel = this.isInFluid(store, x, y, z);
        int fluidAbove = this.isInFluid(store, x, y + 1, z);
        if (fluidLevel == 0 && fluidAbove == 0) return;

        Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
        if (velocity == null) return;

        double waterSurface;
        if (fluidAbove > 0) {
            waterSurface = 2.0;
        } else if (fluidLevel > 0) {
            waterSurface = 1.5;
        } else {
            return;
        }

        double depth = waterSurface - (pos.y - y);
        if (depth <= 0) return;

        Vector3d currentVel = velocity.getVelocity().get(new Vector3d());

        double strength = 30;
        double upwardAccel = strength * depth;

        double damping = 3;
        double dampingAccel = -damping * currentVel.y;

        double netDeltaVy = (upwardAccel + dampingAccel) * dt;

        velocity.set(currentVel.mul(0.98).add(0, netDeltaVy, 0));
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.AFTER, StandardPhysicsTickSystem.class),
                new SystemDependency<>(Order.BEFORE, ItemPrePhysicsSystem.class),
                new SystemDependency<>(Order.AFTER, ItemPhysicsSystem.class)
        );
    }
}
