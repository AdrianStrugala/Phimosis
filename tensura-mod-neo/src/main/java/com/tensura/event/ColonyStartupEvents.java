package com.tensura.event;

import com.tensura.TensuraMod;
import com.tensura.data.DynamicCitizenSpeciesData;
import com.tensura.network.CitizenSpeciesSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Syncs the citizenId → species map to clients on player join.
 * citizenId = MineColonies ICitizenData.getId() — stable across restarts.
 *
 * Species chosen by job and stats. Colony 1 ("Caledonia"), owner: Ehwar.
 */
public class ColonyStartupEvents {

    private static final Map<Integer, String> CITIZEN_TO_SPECIES;

    static {
        Map<Integer, String> m = new HashMap<>();

        // ── Builders (Machamp – 4 arms, built for construction) ──────────────
        m.put(1,  "machamp");   // Zara B. McGee
        m.put(3,  "machamp");   // Reese N. Thorpe
        m.put(4,  "machamp");   // Gracelyn C. Chester
        m.put(51, "machamp");   // Asa Z. Janeli
        m.put(75, "machamp");   // Catalina K. Allington

        // ── Knights (Lucario – warrior, aura fighter) ─────────────────────────
        m.put(2,  "lucario");   // Rayne R. McKenzie
        m.put(8,  "lucario");   // Harlow Q. Collard
        m.put(19, "lucario");   // Leroy D. Gill
        m.put(22, "lucario");   // Blaire C. Goldwell
        m.put(33, "lucario");   // Dorian H. Allington
        m.put(41, "lucario");   // Rayan A. Grobbam
        m.put(46, "lucario");   // Brooke G. Golding
        m.put(48, "lucario");   // Odin A. Harris
        m.put(56, "lucario");   // Joshua Z. Asplin
        m.put(60, "lucario");   // Kamryn B. Audlington
        m.put(65, "lucario");   // Sergio M. Janeli
        m.put(66, "lucario");   // Brantley N. Greene

        // ── Rangers (Decidueye – archer owl) ──────────────────────────────────
        m.put(9,  "decidueye"); // Kishore D. Collard
        m.put(21, "decidueye"); // Salvatore J. Pericherla
        m.put(24, "decidueye"); // Jaelyn B. Cheverell
        m.put(35, "decidueye"); // Mauricio C. Audlington
        m.put(42, "decidueye"); // Itzayana T. Janeli
        m.put(43, "decidueye"); // Karla C. Janeli
        m.put(45, "decidueye"); // Aspen A. Claybrook
        m.put(63, "decidueye"); // Rex A. Clerk
        m.put(73, "decidueye"); // Amaia A. Grimbald

        // ── Researchers (Alakazam – genius, IQ 5000) ──────────────────────────
        m.put(6,  "alakazam");  // Adrien K. McKenzie
        m.put(17, "alakazam");  // Tommy S. Cranford
        m.put(26, "alakazam");  // Mabel C. Collard
        m.put(55, "alakazam");  // Mike X. Coppinger

        // ── Miners (Dugtrio – digs underground) ───────────────────────────────
        m.put(18, "dugtrio");   // Laylah W. Karpinksi
        m.put(54, "dugtrio");   // Kendall A. Karpinksi

        // ── Teachers (Hypno – teaches through hypnosis) ───────────────────────
        m.put(11, "hypno");     // Waverly Z. Grove
        m.put(79, "hypno");     // Brooklyn C. Gomershall

        // ── Archer Training (Farfetch'd – carries weapon) ─────────────────────
        m.put(15, "farfetchd"); // Troy V. Cheverell
        m.put(30, "farfetchd"); // Florence B. Cressy
        m.put(36, "farfetchd"); // Marquis G. Skellett
        m.put(47, "farfetchd"); // Sariah F. Crocker

        // ── Combat Training (Machop – fighting trainee) ───────────────────────
        m.put(25, "machop");    // Ari C. Cortez
        m.put(40, "machop");    // Duran T. Fudd
        m.put(76, "machop");    // Aya A. Karpinksi
        m.put(93, "machop");    // Aniya W. Janeli

        // ── Students (Abra – studious, always sleeping/studying) ──────────────
        m.put(27, "abra");      // Kyleigh G. Clerk
        m.put(34, "abra");      // Katalina C. Cheverell
        m.put(49, "abra");      // Kensley H. Baker
        m.put(53, "abra");      // Melvin C. Grove
        m.put(58, "abra");      // Terry M. Reeves
        m.put(62, "abra");      // Dalary S. Chowne
        m.put(68, "abra");      // Tyler T. Gomershall
        m.put(71, "abra");      // Abdiel M. Robinson

        // ── Deliverymen (Rapidash – fastest courier) ──────────────────────────
        m.put(31, "rapidash");  // Isaiah P. Cressy
        m.put(32, "rapidash");  // Sergio G. Chester
        m.put(50, "rapidash");  // Chana B. Cheverell
        m.put(52, "rapidash");  // Malaya A. Allington
        m.put(88, "rapidash");  // Gideon G. Clerk
        m.put(89, "rapidash");  // Alannah G. Cowill

        // ── Unique jobs ───────────────────────────────────────────────────────
        m.put(5,  "politoed");  // Asa K. Chester       – fisherman
        m.put(7,  "magmar");    // Jaxen G. Allington   – cook
        m.put(12, "beedrill");  // Rachel K. Hammer     – fletcher (stingers = arrows)
        m.put(13, "tauros");    // Aubrielle C. Cheverell – cowboy
        m.put(14, "magneton");  // Jayson T. Barber     – mechanic
        m.put(16, "mareep");    // Spencer B. Cristemas – shepherd (wool sheep)
        m.put(23, "tangela");   // Amalia S. Cockayne   – druid
        m.put(28, "sceptile");  // Maddox C. Chernock   – lumberjack
        m.put(29, "magcargo");  // Myah C. Goldwell     – stonesmeltery
        m.put(37, "magmortar"); // Aaliyah O. Coffin    – blacksmith
        m.put(39, "chansey");   // Alejandro W. Grobbam – healer
        m.put(44, "cyndaquil"); // Damon C. Cortez      – cook assistant
        m.put(57, "swinub");    // Sage X. Atkinson     – swineherder
        m.put(59, "politoed");  // Marvin S. Crugg      – fisherman
        m.put(61, "slurpuff");  // Lane W. Goodrington  – baker
        m.put(64, "politoed");  // Baker A. McKenzie    – fisherman
        m.put(67, "golem");     // Andy C. Williams     – stonemason
        m.put(69, "gengar");    // Kole I. Grimbald     – undertaker
        m.put(70, "conkeldurr");// Peyton A. Ardern     – crusher
        m.put(72, "slugma");    // Legend H. Cobham     – smelter
        m.put(85, "gardevoir"); // Anika A. Grobbam     – enchanter
        m.put(95, "tropius");   // Kamila G. Diehl      – sawmill

        // ── Children / idle citizens ──────────────────────────────────────────
        m.put(10, "eevee");     // Kyng G. Cunningham   – unemployed
        m.put(74, "togepi");    // Jon G. Reeves
        m.put(77, "pichu");     // Oakleigh K. Allington
        m.put(78, "cleffa");    // Kamari C. Gomershall
        m.put(80, "igglybuff"); // Carmen G. Reeves
        m.put(81, "togepi");    // Elinor G. Reeves
        m.put(82, "pichu");     // Lina C. Barton
        m.put(83, "cleffa");    // Luka R. Ballett
        m.put(84, "igglybuff"); // Amrith B. Chernock
        m.put(86, "togepi");    // Monica G. Allington
        m.put(87, "pichu");     // Lucia A. Grobbam
        m.put(90, "cleffa");    // Demetrius K. Battle
        m.put(91, "igglybuff"); // Emberly G. Atkinson
        m.put(92, "togepi");    // Aiden C. Crugg
        m.put(94, "pichu");     // Khari D. Conquest
        m.put(96, "cleffa");    // Grady A. Karpinksi
        m.put(97, "igglybuff"); // Maliah A. Goldwell
        m.put(98, "togepi");    // Johnathan R. Coppinger
        m.put(99, "lycanroc");  // Gwen C. Cheverell    – guard/barbarian

        CITIZEN_TO_SPECIES = Collections.unmodifiableMap(m);
    }

    /** Exposes the hardcoded citizen→species map for non-enrolled citizen recall. */
    public static Map<Integer, String> getHardcodedSpeciesMap() {
        return CITIZEN_TO_SPECIES;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        Map<Integer, String> merged = DynamicCitizenSpeciesData.get(level).mergedWith(CITIZEN_TO_SPECIES);
        CitizenSpeciesSyncPacket.sendToPlayer(player, merged);
        TensuraMod.LOGGER.debug("[Tensura] Sent citizen species map ({} entries) to {}",
                merged.size(), player.getName().getString());
    }

    /**
     * Sends the merged (hardcoded + dynamic) species map to every online player.
     * Called after enrollment or recall to keep all clients in sync.
     */
    public static void broadcastSpeciesMap(ServerLevel level) {
        Map<Integer, String> merged = DynamicCitizenSpeciesData.get(level).mergedWith(CITIZEN_TO_SPECIES);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            CitizenSpeciesSyncPacket.sendToPlayer(player, merged);
        }
        TensuraMod.LOGGER.debug("[Tensura] Broadcast citizen species map ({} entries) to all players", merged.size());
    }
}
