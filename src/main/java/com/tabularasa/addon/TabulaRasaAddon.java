package com.tabularasa.addon;

import com.tabularasa.addon.modules.TabulaRasaScanner;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class TabulaRasaAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Modules.get().add(new TabulaRasaScanner());
        // Future: add HUDs, commands, config modules here
    }

    @Override
    public String getPackage() {
        return "com.tabularasa.addon";
    }
}