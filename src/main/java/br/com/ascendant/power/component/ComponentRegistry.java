package br.com.ascendant.power.component;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry mapping component type IDs to their JSON deserializer factories.
 */
public final class ComponentRegistry {
    private static final Map<String, Function<JsonObject, IPowerComponent>> FACTORIES = new HashMap<>();

    static {
        register(DashComponent.TYPE, DashComponent::fromJson);
        register(BlinkComponent.TYPE, BlinkComponent::fromJson);
        register(LeapComponent.TYPE, LeapComponent::fromJson);
        register(DamageActionComponent.TYPE, DamageActionComponent::fromJson);
        register(HealActionComponent.TYPE, HealActionComponent::fromJson);
        register(KnockbackActionComponent.TYPE, KnockbackActionComponent::fromJson);
        register(PullActionComponent.TYPE, PullActionComponent::fromJson);
        register(ExplosionActionComponent.TYPE, ExplosionActionComponent::fromJson);
        register(LightningActionComponent.TYPE, LightningActionComponent::fromJson);
        register(AreaEffectActionComponent.TYPE, AreaEffectActionComponent::fromJson);
        register(ShieldActionComponent.TYPE, ShieldActionComponent::fromJson);
        register(AttributeModifierComponent.TYPE, AttributeModifierComponent::fromJson);
        register(DamageMitigationComponent.TYPE, DamageMitigationComponent::fromJson);
        register(WaterAdaptationComponent.TYPE, WaterAdaptationComponent::fromJson);
        register(NightVisionComponent.TYPE, NightVisionComponent::fromJson);
        register(ThermalVisionComponent.TYPE, ThermalVisionComponent::fromJson);
        register(FlightActionComponent.TYPE, FlightActionComponent::fromJson);
        register(InvisibilityComponent.TYPE, InvisibilityComponent::fromJson);
        register(LevitationComponent.TYPE, LevitationComponent::fromJson);
        register(VortexActionComponent.TYPE, VortexActionComponent::fromJson);
        register(SummonEntityComponent.TYPE, SummonEntityComponent::fromJson);
        register(PhaseComponent.TYPE, PhaseComponent::fromJson);
        register(SoundParticleComponent.TYPE, SoundParticleComponent::fromJson);
        register(TemplateComponent.TYPE, TemplateComponent::fromJson);
    }

    private ComponentRegistry() {
    }

    public static void register(String type, Function<JsonObject, IPowerComponent> factory) {
        FACTORIES.put(type, factory);
    }

    public static IPowerComponent deserialize(JsonObject json) {
        if (!json.has("type")) {
            return TemplateComponent.fromJson(json);
        }
        String type = json.get("type").getAsString();
        Function<JsonObject, IPowerComponent> factory = FACTORIES.get(type);
        if (factory != null) {
            return factory.apply(json);
        }
        return TemplateComponent.fromJson(json);
    }
}
