package fr.irux.fk2000.client;

import com.mojang.logging.LogUtils;
import fr.irux.fk2000.registry.ModContent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import java.util.HashSet;
import java.util.Set;

/** Optional Entity Culling API integration, restricted to FK moving entities. */
@Mod.EventBusSubscriber(modid = "iruxfk2000", value = Dist.CLIENT)
public final class ClientCullingCompatibility {
    private static int ticks;
    private static boolean announced, failed;
    private ClientCullingCompatibility() {}
    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || failed || !ModList.get().isLoaded("entityculling")) return;
        if (++ticks % 200 != 1) return;
        try {
            Class<?> api = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
            Object instance = api.getField("instance").get(null);
            if (instance == null) return;
            for (String name : new String[]{"tickCullWhitelists", "entityWhitelist"}) {
                var field = api.getField(name);
                if (!(field.get(instance) instanceof Set<?> existing)) return;
                Set<Object> replacement = new HashSet<>(existing);
                replacement.add(ModContent.VEHICLE.get());
                replacement.add(ModContent.MISSILE.get());
                replacement.add(ModContent.TUBE_CAP.get());
                field.set(instance, replacement);
            }
            if (!announced) {
                LogUtils.getLogger().info("FK-2000: Entity Culling render and tick whitelists active for truck, missile and tube cap");
                announced = true;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true;
            LogUtils.getLogger().warn("FK-2000: optional Entity Culling API unavailable; network pose fallback remains active", error);
        }
    }
}
