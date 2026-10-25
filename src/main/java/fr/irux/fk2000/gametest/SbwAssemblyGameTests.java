package fr.irux.fk2000.gametest;

import fr.irux.fk2000.compat.SbwAssemblyIntegration;
import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Exercises the installed SBW recipe/menu/block, rather than an imitation crafting path. */
@GameTestHolder("iruxfk2000_sbw")
@PrefixGameTestTemplate(false)
public final class SbwAssemblyGameTests {
    private SbwAssemblyGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60, batch = "sbw_assembly")
    public static void nativeAssemblyConsumesMaterialsAndReturnsVehicleCrate(GameTestHelper helper) {
        ServerPlayer player = VehicleGameTests.onlinePlayerFixture(helper);
        try {
            Recipe<?> recipe = recipe(helper);
            ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
            assertCrate(helper, result);
            Inventory inventory = player.getInventory();
            inventory.clearContent();
            inventory.add(new ItemStack(Items.IRON_INGOT, 192));
            inventory.add(new ItemStack(Items.COPPER_INGOT, 64));
            inventory.add(new ItemStack(Items.REDSTONE, 48));
            inventory.add(new ItemStack(Items.BLACK_WOOL, 24));
            inventory.add(new ItemStack(Items.GLASS_PANE, 16));
            inventory.add(new ItemStack(Items.PISTON, 8));
            inventory.add(new ItemStack(Items.DIAMOND, 8));
            inventory.add(new ItemStack(ModContent.MISSILE_ITEM.get(), 8));
            inventory.add(new ItemStack(ModContent.REMOTE.get()));
            Class<?> menuClass = Class.forName("com.atsuishio.superbwarfare.inventory.menu.VehicleAssemblingMenu");
            AbstractContainerMenu menu = (AbstractContainerMenu) menuClass.getConstructor(int.class, Inventory.class).newInstance(7, inventory);
            player.containerMenu = menu;
            var assemble = menuClass.getMethod("assembleVehicle", ResourceLocation.class, ServerPlayer.class);
            assemble.invoke(menu, SbwAssemblyIntegration.RECIPE, player);
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3));
            helper.assertTrue(drops.size() == 1, "The real SBW menu must produce one dropped crate");
            assertCrate(helper, drops.get(0).getItem());
            helper.assertTrue(inventory.isEmpty(), "The exact native assembly ingredients must be consumed once");
            assemble.invoke(menu, SbwAssemblyIntegration.RECIPE, player);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3)).size() == 1,
                    "Repeating assembly without materials must not duplicate a crate");
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            helper.fail("Native SBW assembly API failed: " + error);
        } finally {
            VehicleGameTests.removePlayerFixture(helper, player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "sbw_assembly")
    public static void nativeCrowbarDeploymentChecksLongChassisAndClaimsOwner(GameTestHelper helper) {
        ServerPlayer player = VehicleGameTests.onlinePlayerFixture(helper);
        ItemStack crate = recipe(helper).getResultItem(helper.getLevel().registryAccess()).copy();
        assertCrate(helper, crate);
        BlockPos pos = helper.absolutePos(new BlockPos(16, 2, 16));
        BlockHitResult placement = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false);
        player.setItemInHand(InteractionHand.MAIN_HAND, crate);
        helper.assertTrue(((BlockItem) crate.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, crate, placement)).consumesAction(),
                "The native SBW container item must place its real block");
        helper.assertTrue(SbwAssemblyIntegration.isFkCrate(helper.getLevel().getBlockEntity(pos)), "Placed crate must retain FK entity NBT");
        Item crowbar = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "crowbar"));
        helper.assertTrue(crowbar != null && crowbar != Items.AIR, "The release's actual crowbar must exist");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(crowbar));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        BlockPos obstruction = pos.offset(0, 1, 4);
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        var blocked = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        MinecraftForge.EVENT_BUS.post(blocked);
        helper.assertTrue(blocked.isCanceled(), "An obstacle four blocks along the long chassis must block deployment before opening");
        helper.assertTrue(SbwAssemblyIntegration.isFkCrate(helper.getLevel().getBlockEntity(pos)), "Blocked deployment must preserve the filled crate");
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState());
        var clear = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        MinecraftForge.EVENT_BUS.post(clear);
        helper.assertFalse(clear.isCanceled(), "A clear, loaded deployment footprint must accept the real crowbar");
        CompoundTag saved = helper.getLevel().getBlockEntity(pos).saveWithoutMetadata();
        helper.assertTrue(saved.getCompound("Entity").hasUUID("Owner")
                        && player.getUUID().equals(saved.getCompound("Entity").getUUID("Owner")),
                "The opening player must be recorded as vehicle owner before the native animation starts");
        var state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(state.getBlock().use(state, helper.getLevel(), pos, player, InteractionHand.MAIN_HAND, hit).consumesAction(),
                "SBW's own block use must start its crowbar animation");
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(helper.getLevel().isEmptyBlock(pos), "SBW must remove its crate after the deployment animation");
            var vehicles = helper.getLevel().getEntitiesOfClass(Fk2000Entity.class, new net.minecraft.world.phys.AABB(pos).inflate(8));
            helper.assertTrue(vehicles.size() == 1, "The real SBW deployment must produce exactly one FK-2000");
            helper.assertTrue(player.getUUID().equals(vehicles.get(0).getOwnerUUID()), "Deployed vehicle must retain the opening player's ownership");
            helper.assertTrue(vehicles.get(0).isAlive() && vehicles.get(0).getHealth() > 0, "Deployed vehicle must have working health");
            helper.assertTrue(vehicles.get(0).getMissileCount() == ModConfig.MISSILE_CAPACITY.get(),
                    "A newly assembled vehicle must contain the missile load paid for by its assembly recipe");
            helper.assertTrue(vehicles.get(0).isRadarEnabled() == ModConfig.AUTO_RADAR.get(),
                    "A newly assembled vehicle must retain configured initial radar behavior");
            VehicleGameTests.removePlayerFixture(helper, player);
            helper.succeed();
        });
    }

    private static Recipe<?> recipe(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(SbwAssemblyIntegration.RECIPE).orElse(null);
        helper.assertTrue(recipe != null, "The actual installed SBW serializer must load the FK assembly recipe");
        return recipe;
    }

    private static void assertCrate(GameTestHelper helper, ItemStack stack) {
        helper.assertTrue(new ResourceLocation("superbwarfare", "container").equals(ForgeRegistries.ITEMS.getKey(stack.getItem())),
                "The assembly result must be SBW's native orange vehicle crate");
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        helper.assertTrue(tag != null && "iruxfk2000:fk2000".equals(tag.getString("EntityType")), "The crate must identify the FK-2000 entity");
    }
}
