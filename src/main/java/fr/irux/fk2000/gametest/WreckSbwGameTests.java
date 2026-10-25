package fr.irux.fk2000.gametest;

import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.entity.vehicle.WreckEntity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses the installed SBW crowbar and its real tool tag for survival carcass cleanup. */
@GameTestHolder("iruxfk2000_sbw")
@PrefixGameTestTemplate(false)
public final class WreckSbwGameTests {
    private WreckSbwGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "sbw_compatibility")
    public static void onlyCrouchedOwnerCanClearCarcassWithNativeCrowbar(GameTestHelper helper) {
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        ServerPlayer intruder = VehicleGameTests.onlinePlayerFixture(helper);
        Fk2000Entity truck = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        truck.setOwnerUUID(owner.getUUID());
        WreckEntity wreck = ModContent.WRECK.get().create(helper.getLevel());
        helper.assertTrue(wreck != null, "The registered wreck must construct");
        wreck.initializeFrom(truck);
        truck.discard();
        helper.assertTrue(helper.getLevel().addFreshEntity(wreck), "The native crowbar scenario must use a real physical wreck");
        owner.moveTo(wreck.position().add(-3, 0, 0));
        intruder.moveTo(wreck.position().add(3, 0, 0));
        owner.getAbilities().instabuild = false;
        intruder.getAbilities().instabuild = false;
        Item crowbar = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "crowbar"));
        helper.assertTrue(crowbar != null && crowbar != Items.AIR, "An actual SBW release and crowbar are required");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(crowbar));
        intruder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(crowbar));
        int originalDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, wreck.getBoundingBox().inflate(2)).size();
        owner.setShiftKeyDown(false);
        helper.assertTrue(wreck.interact(owner, InteractionHand.MAIN_HAND) == InteractionResult.PASS && !wreck.isRemoved(),
                "An ordinary right click must not accidentally remove a carcass");
        intruder.setShiftKeyDown(true);
        helper.assertTrue(wreck.interact(intruder, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && !wreck.isRemoved(),
                "An intruder's real SBW crowbar must not clear another player's carcass");
        owner.setShiftKeyDown(true);
        helper.assertTrue(wreck.interact(owner, InteractionHand.OFF_HAND) == InteractionResult.PASS && !wreck.isRemoved(),
                "Only the deliberate main-hand crowbar action may remove a wreck");
        Vec3 near = owner.position();
        owner.moveTo(wreck.position().add(-9, 0, 0));
        helper.assertTrue(wreck.interact(owner, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && !wreck.isRemoved(),
                "A distant owner may not forge a crowbar interaction");
        owner.moveTo(near);
        helper.assertTrue(wreck.interact(owner, InteractionHand.MAIN_HAND).consumesAction() && wreck.isRemoved(),
                "Crouch-right-click with the native crowbar must clear the owner's carcass in survival");
        helper.assertTrue(wreck.interact(owner, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
                "Repeating an interaction on an already removed wreck must not perform another cleanup");
        helper.assertTrue(owner.getMainHandItem().getCount() == 1 && owner.getMainHandItem().is(crowbar),
                "Clearing a carcass must preserve the crowbar");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, wreck.getBoundingBox().inflate(2)).size() == originalDrops,
                "Clearing a carcass must not create salvage drops or duplication opportunities");
        VehicleGameTests.removePlayerFixture(helper, owner);
        VehicleGameTests.removePlayerFixture(helper, intruder);
        helper.succeed();
    }
}
