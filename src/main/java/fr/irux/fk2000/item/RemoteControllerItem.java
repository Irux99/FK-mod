package fr.irux.fk2000.item;

import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public final class RemoteControllerItem extends Item {
    public RemoteControllerItem(Properties properties) { super(properties); }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Fk2000Entity vehicle) || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!vehicle.canControl(player)) {
            player.displayClientMessage(Component.translatable("message.iruxfk2000.unauthorized"), true);
            return InteractionResult.FAIL;
        }
        // Player.interactOn passes a copy in creative mode. Bind the real inventory
        // stack so its association survives the interaction and synchronizes.
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof RemoteControllerItem)) return InteractionResult.FAIL;
        CompoundTag tag = held.getOrCreateTag();
        tag.putUUID("Vehicle", vehicle.getUUID());
        tag.putString("Dimension", vehicle.level().dimension().location().toString());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.iruxfk2000.paired"), true);
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!ModNetwork.openRemote(serverPlayer, stack))
                player.displayClientMessage(Component.translatable("message.iruxfk2000.unavailable"), true);
            player.getCooldowns().addCooldown(this, 5);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    public static @Nullable UUID boundVehicle(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return stack.getItem() instanceof RemoteControllerItem && tag != null && tag.hasUUID("Vehicle") ? tag.getUUID("Vehicle") : null;
    }
    public static boolean boundTo(ItemStack stack, Fk2000Entity vehicle) {
        CompoundTag tag = stack.getTag();
        return vehicle.getUUID().equals(boundVehicle(stack)) && tag != null
            && vehicle.level().dimension().location().toString().equals(tag.getString("Dimension"));
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.iruxfk2000.remote"));
        if (boundVehicle(stack) != null) tooltip.add(Component.translatable("tooltip.iruxfk2000.paired"));
    }
}
