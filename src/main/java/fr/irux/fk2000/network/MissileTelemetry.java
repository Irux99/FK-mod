package fr.irux.fk2000.network;

import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Only the authenticated launcher's own active flights enter its telemetry. */
public final class MissileTelemetry {
    public record Track(int id, Vec3 position, Vec3 velocity) {}
    public static List<Track> scan(Fk2000Entity launcher) {
        List<Track> result = new ArrayList<>();
        if (launcher.level() instanceof ServerLevel level) for (var entity : level.getAllEntities()) {
            if (entity instanceof MissileEntity missile && missile.isAlive() && launcher.getUUID().equals(missile.getLauncherUUID())) {
                result.add(new Track(missile.getId(),missile.position(),missile.getDeltaMovement()));
                if (result.size() == 32) break;
            }
        }
        return List.copyOf(result);
    }
    private MissileTelemetry() {}
}
