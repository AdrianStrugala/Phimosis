package com.tensura.engine;

import java.util.List;

public class SpellDefinition {

    public String pokemon_type = "normal";
    public String category = "special";
    /** Direct base damage in HP. Negative values preserve the legacy multiplier formula. */
    public double power = -1.0;
    public String school = "generic";
    public int cooldown_ticks = 100;
    public int cast_time_ticks = 0;
    public int charges = 1;
    public int charge_recovery_ticks = 0;
    public Targeting targeting = new Targeting();
    public Delivery delivery = new Delivery();
    public List<Impact> impact = List.of();
    public Visual visual = new Visual();
    public Sound sound = new Sound();

    public static class Targeting {
        /** "aim" | "area" | "self" | "beam" */
        public String type = "aim";
        public double range = 16.0;
        /** For area/cloud: radius of effect */
        public double radius = 0.0;
        public double width = 1.0;
        public int max_targets = 0;
    }

    public static class Delivery {
        /**
         * "projectile"  — travels as entity projectile
         * "instant"     — immediate hit on resolved targets
         * "self"        — affects only caster
         * "beam"        — instant raycast hitting all entities in a line
         * "meteor"      — falls from above onto target position (AoE on landing)
         * "cloud"       — spawns lingering cloud at target position
         * "dash"        — moves the caster along a collision-checked path
         * "channel_beam" — applies beam impacts periodically while active
         * "delayed_area" — telegraphs a fixed area before applying impacts
         * "moving_zone"  — moves and applies periodic area impacts
         * "protective_aura" — follows the caster and mitigates allied damage
         * "channel_cone" — repeatedly applies impacts in a forward cone
         * "wave"        — moves a ground-level front through enemies
         * "trap"        — persists at a location and triggers on entry
         * "melee_combo" — applies a timed sequence of close-range hits
         * "teleport_strike" — teleports behind an aimed target and strikes
         * "ricochet_beam" — instant beam that redirects to one extra target
         */
        public String type = "projectile";
        public double speed = 1.5;
        public int projectile_count = 1;
        public double spread_degrees = 0.0;
        public double distance = 0.0;
        public int duration_ticks = 0;
        public int delay_ticks = 0;
        public int tick_interval_ticks = 0;
        public int recovery_ticks = 0;
        public boolean steerable = false;
        public double movement_speed = 0.0;
        public double homing_strength = 0.0;
        public double pull_strength = 0.0;
        public double cone_angle = 45.0;
        public int combo_hits = 1;
        public int combo_interval_ticks = 4;
        public int bounce_count = 0;
    }

    public static class Impact {
        /** Damage, status, movement, healing, guard, or combo-specific effect. */
        public String type = "damage";
        /** "target" | "caster" */
        public String recipient = "target";
        // damage
        public double damage_multiplier = 1.0;
        // status_effect
        public String effect = "";
        public int duration = 100;
        public int amplifier = 0;
        public double chance = 1.0;
        public boolean ambient = false;
        public boolean show_particles = true;
        public boolean show_icon = true;
        // fire
        public int seconds = 3;
        // knockback
        public double strength = 1.0;
        // heal
        public double amount = 4.0;
        // mitigation
        public double reduction = 0.0;
    }

    public static class Visual {
        public String cast_animation = "cast_point";
        public String projectile = "";
        public String trail = "";
        public String telegraph = "";
        public String impact = "";
        public String aftermath = "";
    }

    public static class Sound {
        public String cast = "";
        public String travel = "";
        public String impact = "";
        public String loop = "";
    }
}
