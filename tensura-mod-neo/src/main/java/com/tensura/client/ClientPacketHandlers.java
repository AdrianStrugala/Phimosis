package com.tensura.client;

import com.tensura.gui.PredatorCodexScreen;
import com.tensura.gui.SkillSelectScreen;
import com.tensura.spell.SkillEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandlers {

    public static void openSkillSelect(List<SkillEntry> skills, String mobName, ResourceLocation species) {
        Minecraft.getInstance().setScreen(new SkillSelectScreen(skills, mobName, species));
    }

    public static void openCodex(List<ResourceLocation> absorbed) {
        Minecraft.getInstance().setScreen(new PredatorCodexScreen(absorbed));
    }
}
