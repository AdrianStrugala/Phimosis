package com.tensura.registry;

import com.tensura.TensuraMod;
import com.tensura.block.RecallStationBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TensuraBlockRegistry {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, TensuraMod.MOD_ID);

    public static final DeferredHolder<Block, RecallStationBlock> RECALL_STATION =
            BLOCKS.register("recall_station", RecallStationBlock::new);
}
