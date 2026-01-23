package su.brim.goatrider.manager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Player;
import su.brim.goatrider.GoatRiderPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для отслеживания игроков, которые едут на козлах.
 * Использует ConcurrentHashMap для потокобезопасности в Folia.
 */
public class RidingManager {

    private final GoatRiderPlugin plugin;
    
    // Хранение данных о наездниках: UUID игрока -> UUID козла
    private final Map<UUID, UUID> riders = new ConcurrentHashMap<>();
    
    // Хранение количества оставшихся прыжков для каждого игрока
    private final Map<UUID, Integer> jumpCounts = new ConcurrentHashMap<>();
    
    // Хранение задач контроля для каждого козла
    private final Map<UUID, ScheduledTask> controlTasks = new ConcurrentHashMap<>();
    
    // Кулдаун на урон тарана для каждой цели (UUID цели -> время последнего урона)
    private final Map<UUID, Long> ramCooldowns = new ConcurrentHashMap<>();
    
    // Кулдаун на прыжок для каждого игрока (чтобы избежать спама)
    private final Map<UUID, Long> jumpCooldowns = new ConcurrentHashMap<>();
    
    // Кулдаун урона в миллисекундах (500мс = 10 тиков)
    private static final long RAM_COOLDOWN_MS = 500L;
    
    // Кулдаун прыжка в миллисекундах (200мс = 4 тика)
    private static final long JUMP_COOLDOWN_MS = 200L;

    public RidingManager(GoatRiderPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Регистрирует игрока как наездника козла.
     */
    public void addRider(Player player, Goat goat) {
        riders.put(player.getUniqueId(), goat.getUniqueId());
        resetJumps(player);
    }

    /**
     * Удаляет игрока из списка наездников.
     */
    public void removeRider(Player player) {
        riders.remove(player.getUniqueId());
        jumpCounts.remove(player.getUniqueId());
        jumpCooldowns.remove(player.getUniqueId());
        cancelControlTask(player.getUniqueId());
    }

    /**
     * Проверяет, едет ли игрок на козле.
     */
    public boolean isRiding(Player player) {
        return riders.containsKey(player.getUniqueId());
    }

    /**
     * Получает UUID козла, на котором едет игрок.
     */
    public UUID getGoatUUID(Player player) {
        return riders.get(player.getUniqueId());
    }

    /**
     * Сбрасывает счётчик прыжков игрока.
     */
    public void resetJumps(Player player) {
        jumpCounts.put(player.getUniqueId(), plugin.getConfigManager().getExtraJumps());
    }

    /**
     * Использует один прыжок. Возвращает true, если прыжок был доступен.
     */
    public boolean useJump(Player player) {
        int remaining = jumpCounts.getOrDefault(player.getUniqueId(), 0);
        if (remaining > 0) {
            jumpCounts.put(player.getUniqueId(), remaining - 1);
            return true;
        }
        return false;
    }

    /**
     * Проверяет, есть ли у игрока доступные прыжки.
     */
    public boolean hasJumpsRemaining(Player player) {
        return jumpCounts.getOrDefault(player.getUniqueId(), 0) > 0;
    }
    
    /**
     * Проверяет и устанавливает кулдаун прыжка.
     * Возвращает true, если прыжок разрешён (кулдаун прошёл).
     */
    public boolean canJump(Player player) {
        long now = System.currentTimeMillis();
        Long lastJump = jumpCooldowns.get(player.getUniqueId());
        
        if (lastJump == null || now - lastJump >= JUMP_COOLDOWN_MS) {
            jumpCooldowns.put(player.getUniqueId(), now);
            return true;
        }
        return false;
    }
    
    /**
     * Проверяет, можно ли нанести урон тараном данной цели.
     * Возвращает true и устанавливает кулдаун, если урон разрешён.
     */
    public boolean canRamDamage(UUID targetUUID) {
        long now = System.currentTimeMillis();
        Long lastDamage = ramCooldowns.get(targetUUID);
        
        if (lastDamage == null || now - lastDamage >= RAM_COOLDOWN_MS) {
            ramCooldowns.put(targetUUID, now);
            return true;
        }
        return false;
    }
    
    /**
     * Очищает устаревшие кулдауны (вызывать периодически).
     */
    public void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        ramCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > RAM_COOLDOWN_MS * 2);
    }

    /**
     * Сохраняет задачу контроля для козла.
     */
    public void setControlTask(UUID goatUUID, ScheduledTask task) {
        ScheduledTask oldTask = controlTasks.put(goatUUID, task);
        if (oldTask != null && !oldTask.isCancelled()) {
            oldTask.cancel();
        }
    }

    /**
     * Отменяет задачу контроля для козла.
     */
    public void cancelControlTask(UUID goatUUID) {
        ScheduledTask task = controlTasks.remove(goatUUID);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Высаживает всех игроков с козлов (при отключении плагина).
     */
    public void dismountAll() {
        // Отменяем все задачи
        for (ScheduledTask task : controlTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        controlTasks.clear();
        
        // Очищаем данные
        riders.clear();
        jumpCounts.clear();
        jumpCooldowns.clear();
        ramCooldowns.clear();
    }

    /**
     * Получает количество активных наездников.
     */
    public int getRiderCount() {
        return riders.size();
    }
}
