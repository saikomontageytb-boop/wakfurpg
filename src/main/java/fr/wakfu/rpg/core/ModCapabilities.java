package fr.wakfu.rpg.core;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public class ModCapabilities {
    public static final Capability<PlayerRpgData> RPG =
            CapabilityManager.get(new CapabilityToken<PlayerRpgData>() {});

    private ModCapabilities() {}
}
