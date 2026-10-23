package br.com.ascendant.power.component;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Base contract for reusable power and ability components.
 * Components encapsulate distinct gameplay mechanics without needing unique Java classes per power.
 */
public interface IPowerComponent {

    /**
     * Unique identifier for this component type (e.g. "ascendant:dash", "ascendant:shield").
     */
    String getType();

    /**
     * Returns true if this component contains real, active server gameplay logic.
     * If false, this component is a template or schema placeholder.
     */
    boolean isFunctional();

    /**
     * Executes the component action when an active ability or power triggers.
     * @return true if the action was successfully performed.
     */
    default boolean execute(ComponentContext context) {
        return false;
    }

    /**
     * Invoked every player tick on the server if the power is active, unlocked, or toggled on.
     */
    default void onPlayerTick(ServerPlayer player) {
    }

    /**
     * Invoked when the player attacks an entity (LivingHurtEvent on the target).
     */
    default void onAttack(ServerPlayer player, LivingHurtEvent event) {
    }

    /**
     * Invoked when the player takes damage (LivingHurtEvent on the player).
     */
    default void onHurt(ServerPlayer player, LivingHurtEvent event) {
    }

    /**
     * Invoked when the player experiences a fall event.
     */
    default void onFall(ServerPlayer player, LivingFallEvent event) {
    }

    /**
     * Invoked when a toggle power is enabled or disabled.
     */
    default void onToggle(ServerPlayer player, boolean enabled) {
    }

    /**
     * Validates a requested toggle state before the runtime records it. Returning false
     * refuses the request so the player receives a real failure instead of a silent no-op.
     */
    default boolean canToggle(ServerPlayer player, boolean enable) {
        return true;
    }

    /**
     * Serializes this component into JSON for content pack exports or data-driven saving.
     */
    JsonObject toJson();
}
