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
         * "contact_aura" — follows the caster and impacts each contacted target once
         * "orbit_release" — follows the caster until recast or automatic radial release
         * "phase_movement" — steerable phased movement ending in a contact strike
         * "pulse_ring" — emits timed outward/returning radial phases
         * "barrier_wall" — places a finite directional wall that intercepts attacks
         * "channel_cone" — repeatedly applies impacts in a forward cone
         * "wave"        — moves a ground-level front through enemies
         * "trap"        — persists at a location and triggers on entry
         * "melee_combo" — applies a timed sequence of close-range hits
         * "teleport_strike" — teleports behind an aimed target and strikes
         * "ricochet_beam" — instant beam that redirects to one extra target
         * "arc_strike"  — instant targetless melee arc in front of the caster
         * "grab"        — targeted close-range impact that throws the victim
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
        public boolean hold_to_channel = false;
        public boolean charge_release = false;
        public int minimum_charge_ticks = 0;
        public int maximum_charge_ticks = 0;
        public double movement_speed = 0.0;
        public double homing_strength = 0.0;
        public double pull_strength = 0.0;
        public double cone_angle = 45.0;
        public int combo_hits = 1;
        public int combo_interval_ticks = 4;
        public int bounce_count = 0;
        public boolean return_to_origin = false;
        /**
         * Delayed blasts walking down a beam's trace after it fires (Hyper Beam's "Terminal
         * Line"). Zero leaves every other beam untouched. Blast {@code i} lands at
         * {@code (i+1) * 0.28} of the trace after {@code (i+1) * 3} ticks - the same spots and
         * timings the client already draws with {@code ProgrammaticSpellFx#hyperBeamAftershock},
         * so the damage arrives where the player sees the flash. Each blast is a splash of
         * {@code targeting.radius} scaled by the multiplier below.
         */
        public int aftershock_count = 0;
        public double aftershock_damage_multiplier = 0.0;
    }

    public static class Impact {
        /** Damage, status, movement, healing, guard, or combo-specific effect. */
        public String type = "damage";
        /** "target" | "caster" */
        public String recipient = "target";
        // damage
        public double damage_multiplier = 1.0;
        public double conditional_multiplier = 1.0;
        public double armor_penetration = 0.0;
        public double max_distance = 0.0;
        // status_effect
        public String effect = "";
        public List<String> effects = List.of();
        public int duration = 100;
        public int amplifier = 0;
        public double chance = 1.0;
        public boolean ambient = false;
        public boolean show_particles = true;
        public boolean show_icon = true;
        public boolean final_hit_only = false;
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
