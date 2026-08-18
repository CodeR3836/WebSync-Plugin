package com.websync;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/**
 * Wires {@link EconomyBridge} to whatever economy plugin Vault has
 * registered (Essentials, CMI, EconomyShopGUI-Premium's Vault module,
 * etc.) — without WebSync ever depending on a specific one of them.
 *
 * This is intentionally the *only* place that touches Vault's API.
 * {@link EconomyBridge} stays a plain functional-interface abstraction
 * so the rest of WebSync (in particular {@link PlayerPayloadFactory})
 * never needs to know Vault exists.
 *
 * Call {@link #setup(Logger)} once, from {@link WebSyncPlugin#onEnable()}.
 * Because {@code plugin.yml} declares {@code softdepend: [Vault]}, Vault
 * (and whatever economy plugin registers with it) will already be fully
 * enabled by the time this runs, if it's installed at all.
 */
final class VaultEconomySupport {

    private VaultEconomySupport() {
    }

    static void setup(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.info("[WebSync] Vault not found — player balance will sync as 0.");
            return;
        }

        RegisteredServiceProvider<Economy> registration;
        try {
            registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        } catch (Throwable t) {
            // Vault is installed but something about querying its service
            // registry blew up (bad/incompatible install, missing class,
            // etc.) — WebSync must keep working regardless, just with
            // balance staying at 0.
            logger.warning("[WebSync] Vault was found but its Economy service could not be queried ("
                    + t.getClass().getSimpleName() + ") — player balance will sync as 0.");
            return;
        }

        if (registration == null || registration.getProvider() == null) {
            logger.info("[WebSync] Vault was found, but no plugin has registered an economy "
                    + "provider with it — player balance will sync as 0. "
                    + "(If you use EconomyShopGUI-Premium, make sure its Vault economy module is enabled.)");
            return;
        }

        Economy economy = registration.getProvider();

        // The provider itself is trusted to be whatever economy plugin
        // registered it; EconomyBridge.getBalance() already guards every
        // call to this lambda in a try/catch, so a misbehaving economy
        // plugin still can't break player syncing.
        EconomyBridge.register(player -> Math.round(economy.getBalance(player)));

        logger.info("[WebSync] Vault economy provider \"" + economy.getName()
                + "\" found — WebSync is using it for player balance.");
    }
}
