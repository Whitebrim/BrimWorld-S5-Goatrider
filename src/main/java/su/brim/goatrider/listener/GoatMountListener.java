package su.brim.goatrider.listener;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDismountEvent;
import su.brim.goatrider.GoatRiderPlugin;
import su.brim.goatrider.manager.ConfigManager;
import su.brim.goatrider.manager.RidingManager;

/**
 * Слушатель событий посадки и высадки с козла.
 */
public class GoatMountListener implements Listener {

    private final GoatRiderPlugin plugin;
    private final ConfigManager config;
    private final RidingManager ridingManager;

    public GoatMountListener(GoatRiderPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.ridingManager = plugin.getRidingManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Игнорируем левую руку, чтобы избежать двойного срабатывания
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity entity = event.getRightClicked();
        
        // Проверяем, что это козёл
        if (!(entity instanceof Goat goat)) {
            return;
        }

        Player player = event.getPlayer();

        // Проверяем права
        if (!player.hasPermission("goatrider.ride")) {
            player.sendMessage(config.formatMessage(config.getNoPermission()));
            return;
        }

        // Проверяем, не занят ли козёл
        if (!goat.getPassengers().isEmpty()) {
            return;
        }

        // Проверяем требование седла
        if (config.isRequireSaddle()) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            
            boolean hasSaddleInHand = mainHand.getType() == Material.SADDLE || 
                                       offHand.getType() == Material.SADDLE;
            
            if (!hasSaddleInHand) {
                player.sendMessage(config.formatMessage(config.getSaddleRequired()));
                return;
            }
        }

        // Отменяем стандартное взаимодействие
        event.setCancelled(true);

        // Сажаем игрока на козла используя EntityScheduler для Folia
        goat.getScheduler().run(plugin, task -> {
            if (goat.isValid() && player.isOnline()) {
                goat.addPassenger(player);
                ridingManager.addRider(player, goat);
                player.sendMessage(config.formatMessage(config.getMountSuccess()));
            }
        }, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDismount(EntityDismountEvent event) {
        Entity dismounted = event.getDismounted();
        Entity entity = event.getEntity();

        // Проверяем, что игрок слез с козла
        if (!(dismounted instanceof Goat) || !(entity instanceof Player player)) {
            return;
        }

        // Проверяем, был ли игрок зарегистрирован как наездник
        if (ridingManager.isRiding(player)) {
            ridingManager.removeRider(player);
            player.sendMessage(config.formatMessage(config.getDismountSuccess()));
        }
    }
}
