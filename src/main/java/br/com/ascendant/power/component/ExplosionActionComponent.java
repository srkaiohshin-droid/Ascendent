package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Creates a controlled kinetic or fiery explosion.
 */
public final class ExplosionActionComponent implements IPowerComponent {
    public static final String TYPE = "ascendant:explosion_action";
    private final float radius;
    private final boolean breakBlocks;
    private final boolean causesFire;

    public ExplosionActionComponent(float radius, boolean breakBlocks, boolean causesFire) {
        this.radius = radius;
        this.breakBlocks = breakBlocks;
        this.causesFire = causesFire;
    }

    public ExplosionActionComponent(float radius) {
        this(radius, false, false);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isFunctional() {
        return true;
    }

    @Override
    public boolean execute(ComponentContext context) {
        ServerPlayer player = context.getPlayer();
        ServerLevel level = context.getLevel();
        Vec3 look = context.getLookDirection();
        Vec3 epicenter = player.position().add(look.scale(2.0D)).add(0, 0.5D, 0);

        Level.ExplosionInteraction interaction = breakBlocks ?
                Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE;

        level.explode(player, null, null, epicenter.x, epicenter.y, epicenter.z,
                radius, causesFire, interaction);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, epicenter.x, epicenter.y, epicenter.z,
                1, 0, 0, 0, 0);
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("radius", radius);
        obj.addProperty("breakBlocks", breakBlocks);
        obj.addProperty("causesFire", causesFire);
        return obj;
    }

    public static ExplosionActionComponent fromJson(JsonObject json) {
        float rad = json.has("radius") ? json.get("radius").getAsFloat() : 3.0F;
        boolean breakB = json.has("breakBlocks") && json.get("breakBlocks").getAsBoolean();
        boolean fire = json.has("causesFire") && json.get("causesFire").getAsBoolean();
        return new ExplosionActionComponent(rad, breakB, fire);
    }
}
