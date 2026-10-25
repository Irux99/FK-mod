package fr.irux.fk2000.gametest;

import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class DeploymentGameTests {
    private DeploymentGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void longChassisPlacementIsAtomicAndClearOfPlayer(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        Vec3 playerPosition = helper.absoluteVec(new Vec3(16.5, 2, 1));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z, 0, 0);
        ItemStack kit = new ItemStack(ModContent.DEPLOYMENT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, kit);
        BlockPos ground = helper.absolutePos(new BlockPos(16, 1, 3));
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, .5, 0), Direction.UP, ground, false));
        // This obstacle is near the far end of the enlarged chassis, outside the
        // old three-block hitbox. A failed placement must keep the survival kit.
        helper.setBlock(16, 3, 14, Blocks.STONE);
        AABB arena = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(32, 96, 32)));
        helper.assertFalse(kit.useOn(context).consumesAction(), "The entire long chassis must be clear before placement");
        helper.assertTrue(kit.getCount() == 1, "A blocked placement must preserve the kit");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(Fk2000Entity.class,
                arena).isEmpty(), "A blocked placement must not leave a vehicle behind");
        helper.setBlock(16, 3, 14, Blocks.AIR);
        helper.assertTrue(kit.useOn(context).consumesAction(), "A cleared placement must deploy the vehicle");
        var vehicles = helper.getLevel().getEntitiesOfClass(Fk2000Entity.class, arena);
        helper.assertTrue(vehicles.size() == 1 && kit.isEmpty(), "Deployment must create one vehicle and consume one kit");
        Fk2000Entity vehicle = vehicles.get(0);
        helper.assertTrue(player.getUUID().equals(vehicle.getOwnerUUID()), "The deploying player must own the truck");
        helper.assertFalse(vehicle.getBoundingBox().intersects(player.getBoundingBox()),
                "The enlarged truck must appear ahead of the player rather than around their feet");
        helper.succeed();
    }
}
