package su.brim.goatrider.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import su.brim.goatrider.GoatRiderPlugin;

public class ConfigManager {

    private final GoatRiderPlugin plugin;
    
    // Настройки
    private boolean requireSaddle;
    private double speed;
    private double jumpStrength;
    private boolean multiJump;
    private int extraJumps;
    private double ramDamage;
    private boolean ramEnabled;
    
    // Сообщения
    private String prefix;
    private String mountSuccess;
    private String dismountSuccess;
    private String noPermission;
    private String saddleRequired;
    private String configReloaded;
    private String noAdminPermission;

    public ConfigManager(GoatRiderPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        
        // Загрузка настроек
        requireSaddle = config.getBoolean("require-saddle", false);
        speed = config.getDouble("speed", 0.25);
        jumpStrength = config.getDouble("jump-strength", 0.8);
        multiJump = config.getBoolean("multi-jump", true);
        extraJumps = config.getInt("extra-jumps", 1);
        ramDamage = config.getDouble("ram-damage", 4.0);
        ramEnabled = config.getBoolean("ram-enabled", true);
        
        // Загрузка сообщений
        prefix = config.getString("messages.prefix", "&8[&6GoatRider&8] ");
        mountSuccess = config.getString("messages.mount-success", "&aВы сели на козла!");
        dismountSuccess = config.getString("messages.dismount-success", "&eВы слезли с козла.");
        noPermission = config.getString("messages.no-permission", "&cУ вас нет прав для езды на козлах!");
        saddleRequired = config.getString("messages.saddle-required", "&cДля езды на козле необходимо седло!");
        configReloaded = config.getString("messages.config-reloaded", "&aКонфигурация перезагружена!");
        noAdminPermission = config.getString("messages.no-admin-permission", "&cУ вас нет прав для этой команды!");
    }

    public Component formatMessage(String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + message);
    }

    public Component formatRawMessage(String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    // Геттеры для настроек
    public boolean isRequireSaddle() {
        return requireSaddle;
    }

    public double getSpeed() {
        return speed;
    }

    public double getJumpStrength() {
        return jumpStrength;
    }

    public boolean isMultiJump() {
        return multiJump;
    }

    public int getExtraJumps() {
        return extraJumps;
    }

    public double getRamDamage() {
        return ramDamage;
    }

    public boolean isRamEnabled() {
        return ramEnabled;
    }

    // Геттеры для сообщений
    public String getMountSuccess() {
        return mountSuccess;
    }

    public String getDismountSuccess() {
        return dismountSuccess;
    }

    public String getNoPermission() {
        return noPermission;
    }

    public String getSaddleRequired() {
        return saddleRequired;
    }

    public String getConfigReloaded() {
        return configReloaded;
    }

    public String getNoAdminPermission() {
        return noAdminPermission;
    }
}
