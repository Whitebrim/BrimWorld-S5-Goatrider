package su.brim.goatrider;

import org.bukkit.plugin.java.JavaPlugin;
import su.brim.goatrider.command.GoatRiderCommand;
import su.brim.goatrider.listener.GoatMountListener;
import su.brim.goatrider.listener.GoatControlListener;
import su.brim.goatrider.manager.ConfigManager;
import su.brim.goatrider.manager.RidingManager;

public class GoatRiderPlugin extends JavaPlugin {

    private static GoatRiderPlugin instance;
    private ConfigManager configManager;
    private RidingManager ridingManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Сохраняем дефолтную конфигурацию
        saveDefaultConfig();
        
        // Инициализация менеджеров
        configManager = new ConfigManager(this);
        ridingManager = new RidingManager(this);
        
        // Регистрация слушателей
        getServer().getPluginManager().registerEvents(new GoatMountListener(this), this);
        getServer().getPluginManager().registerEvents(new GoatControlListener(this), this);
        
        // Регистрация команд
        GoatRiderCommand command = new GoatRiderCommand(this);
        getCommand("goatrider").setExecutor(command);
        getCommand("goatrider").setTabCompleter(command);
        
        getLogger().info("GoatRider успешно загружен!");
    }

    @Override
    public void onDisable() {
        // Высаживаем всех игроков с козлов
        if (ridingManager != null) {
            ridingManager.dismountAll();
        }
        
        getLogger().info("GoatRider выключен.");
    }

    public static GoatRiderPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RidingManager getRidingManager() {
        return ridingManager;
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
    }
}
