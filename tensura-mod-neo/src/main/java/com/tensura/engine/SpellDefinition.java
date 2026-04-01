package com.tensura.engine;

import java.util.List;

public class SpellDefinition {

    public String school = "generic";
    public int cooldown_ticks = 100;
    public Targeting targeting = new Targeting();
    public Delivery delivery = new Delivery();
    public List<Impact> impact = List.of();

    public static class Targeting {
        /** "aim" | "area" | "self" | "beam" */
        public String type = "aim";
        public double range = 16.0;
        /** For area/cloud: radius of effect */
        public double radius = 0.0;
    }

    public static class Delivery {
        /**
         * "projectile"  — travels as entity projectile
         * "instant"     — immediate hit on resolved targets
         * "self"        — affects only caster
         * "beam"        — instant raycast hitting all entities in a line
         * "meteor"      — falls from above onto target position (AoE on landing)
         * "cloud"       — spawns lingering cloud at target position
         */
        public String type = "projectile";
        public double speed = 1.5;
    }

    public static class Impact {
        /** "damage" | "status_effect" | "fire" | "knockback" | "heal" */
        public String type = "damage";
        // damage
        public double damage_multiplier = 1.0;
        // status_effect
        public String effect = "";
        public int duration = 100;
        public int amplifier = 0;
        public double chance = 1.0;
        // fire
        public int seconds = 3;
        // knockback
        public double strength = 1.0;
        // heal
        public double amount = 4.0;
    }
}
