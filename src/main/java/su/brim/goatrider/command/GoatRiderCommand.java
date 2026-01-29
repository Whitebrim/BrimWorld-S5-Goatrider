package su.brim.goatrider.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.brim.goatrider.GoatRiderPlugin;
import su.brim.goatrider.manager.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик команд плагина GoatRider.
 */
public class GoatRiderCommand implements CommandExecutor, TabCompleter {

    private final GoatRiderPlugin plugin;
    private final ConfigManager config;

    public GoatRiderCommand(GoatRiderPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            case "help" -> sendHelp(sender);
            default -> {
                sender.sendMessage(config.formatMessage("&cНеизвестная команда. Используйте /gr help"));
            }
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("goatrider.admin")) {
            sender.sendMessage(config.formatMessage(config.getNoAdminPermission()));
            return;
        }

        plugin.reload();
        sender.sendMessage(config.formatMessage(config.getConfigReloaded()));
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(config.formatRawMessage("&6&l=== GoatRider Info ==="));
        sender.sendMessage(config.formatRawMessage("&7Версия: &f" + plugin.getDescription().getVersion()));
        sender.sendMessage(config.formatRawMessage("&7Активных наездников: &f" + plugin.getRidingManager().getRiderCount()));
        sender.sendMessage(config.formatRawMessage("&7Требуется седло: &f" + (config.isRequireSaddle() ? "Да" : "Нет")));
        sender.sendMessage(config.formatRawMessage("&7Скорость: &f" + config.getSpeed()));
        sender.sendMessage(config.formatRawMessage("&7Множитель спринта: &f" + config.getSprintMultiplier() + "x"));
        sender.sendMessage(config.formatRawMessage("&7Сила прыжка: &f" + config.getJumpStrength()));
        sender.sendMessage(config.formatRawMessage("&7Мульти-прыжок: &f" + (config.isMultiJump() ? "Да (" + config.getExtraJumps() + " доп.)" : "Нет")));
        sender.sendMessage(config.formatRawMessage("&7Урон тарана: &f" + (config.isRamEnabled() ? config.getRamDamage() : "Выключен")));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(config.formatRawMessage("&6&l=== GoatRider Help ==="));
        sender.sendMessage(config.formatRawMessage("&e/gr reload &7- Перезагрузить конфигурацию"));
        sender.sendMessage(config.formatRawMessage("&e/gr info &7- Информация о плагине"));
        sender.sendMessage(config.formatRawMessage("&e/gr help &7- Показать эту справку"));
        sender.sendMessage(config.formatRawMessage(""));
        sender.sendMessage(config.formatRawMessage("&6Управление:"));
        sender.sendMessage(config.formatRawMessage("&7• &fПКМ по козлу &7- сесть"));
        sender.sendMessage(config.formatRawMessage("&7• &fWASD &7- движение"));
        sender.sendMessage(config.formatRawMessage("&7• &fПробел &7- прыжок"));
        sender.sendMessage(config.formatRawMessage("&7• &fДвойное W &7- спринт (ускорение)"));
        sender.sendMessage(config.formatRawMessage("&7• &fShift &7- слезть"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            
            if ("reload".startsWith(input) && sender.hasPermission("goatrider.admin")) {
                completions.add("reload");
            }
            if ("info".startsWith(input)) {
                completions.add("info");
            }
            if ("help".startsWith(input)) {
                completions.add("help");
            }
        }

        return completions;
    }
}
