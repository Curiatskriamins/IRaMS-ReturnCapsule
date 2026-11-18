package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import org.jetbrains.annotations.NotNull;

public class HumanoidNavigation extends GroundPathNavigation {

    private static final int MAX_VISITED_NODES = 96;

    private static final int STUCK_RESET_TICKS = 100;

    private int stuckTicks;

    private net.minecraft.world.phys.Vec3 lastStuckPos;

    public HumanoidNavigation(Mob mob, @NotNull Level level) {
        super(mob, level);
    }

    @Override
    public void recomputePath() {
    }

    @Override
    protected void doStuckDetection(net.minecraft.world.phys.Vec3 position) {
        if (this.lastStuckPos != null && position.distanceToSqr(this.lastStuckPos) > 0.01D) {
            this.stuckTicks = 0;
        } else {
            this.stuckTicks++;
        }
        this.lastStuckPos = position;
        if (this.stuckTicks > STUCK_RESET_TICKS) {
            this.stuckTicks = 0;
            this.stop();
        }
    }

    @Override
    protected @NotNull PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new HumanoidNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, Math.min(maxVisitedNodes, MAX_VISITED_NODES));
    }
}
