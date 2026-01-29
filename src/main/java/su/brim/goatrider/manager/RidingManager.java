package su.brim.goatrider.manager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
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
    
    // NamespacedKey для модификатора атрибута безопасного падения
    private final NamespacedKey safeFallModifierKey;
    
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
    
    // Отслеживание double-tap W для спринта
    private final Map<UUID, Long> lastForwardPress = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> wasForwardPressed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> sprintActive = new ConcurrentHashMap<>();
    
    // Козлы, которым нужно снять модификатор (UUID козла -> время добавления)
    private final Map<UUID, Long> goatsToCleanup = new ConcurrentHashMap<>();
    
    // Кулдаун урона в миллисекундах (500мс = 10 тиков)
    private static final long RAM_COOLDOWN_MS = 500L;
    
    // Кулдаун прыжка в миллисекундах (200мс = 4 тика)
    private static final long JUMP_COOLDOWN_MS = 200L;

    public RidingManager(GoatRiderPlugin plugin) {
        this.plugin = plugin;
        this.safeFallModifierKey = new NamespacedKey(plugin, "goat_rider_safe_fall");
    }

    /**
     * Регистрирует игрока как наездника козла.
     */
    public void addRider(Player player, Goat goat) {
        UUID uuid = player.getUniqueId();
        riders.put(uuid, goat.getUniqueId());
        resetJumps(player);
        // Сбрасываем состояние спринта
        sprintActive.put(uuid, false);
        wasForwardPressed.put(uuid, false);
        lastForwardPress.remove(uuid);
        
        // Добавляем модификатор безопасного падения игроку и козлу
        double fallDistance = plugin.getConfigManager().getFallProtectionDistance();
        applySafeFallModifier(player, fallDistance);
        applySafeFallModifier(goat, fallDistance);
    }

    /**
     * Удаляет игрока из списка наездников.
     */
    public void removeRider(Player player) {
        UUID uuid = player.getUniqueId();
        UUID goatUuid = riders.remove(uuid);
        jumpCounts.remove(uuid);
        jumpCooldowns.remove(uuid);
        lastForwardPress.remove(uuid);
        wasForwardPressed.remove(uuid);
        sprintActive.remove(uuid);
        cancelControlTask(uuid);
        
        // Удаляем модификатор безопасного падения у игрока
        removeSafeFallModifier(player);
        
        // Удаляем модификатор у козла, если найден
        if (goatUuid != null) {
            // Сохраняем UUID козла для последующего удаления модификатора
            // (козёл может быть недоступен сразу, но модификатор снимется при следующей проверке)
            goatsToCleanup.put(goatUuid, System.currentTimeMillis());
        }
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
     * Обновляет состояние спринта на основе нажатия W и кнопки спринта.
     * Реализует логику double-tap W или удержания клавиши спринта.
     * @param player игрок
     * @param isForwardPressed нажата ли клавиша W сейчас
     * @param isSprintKeyPressed нажата ли клавиша спринта (Ctrl)
     * @return true если спринт активен
     */
    public boolean updateSprintState(Player player, boolean isForwardPressed, boolean isSprintKeyPressed) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long doubleTapTime = plugin.getConfigManager().getDoubleTapTime();
        
        boolean wasPressed = wasForwardPressed.getOrDefault(uuid, false);
        boolean isSprinting = sprintActive.getOrDefault(uuid, false);
        
        // Спринт активен если:
        // 1. Нажата кнопка спринта + движение вперёд
        // 2. Или активирован через double-tap W
        
        // Проверяем кнопку спринта (как в ваниле - нужно двигаться вперёд)
        if (isSprintKeyPressed && isForwardPressed) {
            isSprinting = true;
            sprintActive.put(uuid, true);
        }
        // Если W только что нажата (переход false -> true) - проверяем double-tap
        else if (isForwardPressed && !wasPressed) {
            Long lastPress = lastForwardPress.get(uuid);
            
            // Проверяем double-tap
            if (lastPress != null && (now - lastPress) <= doubleTapTime) {
                // Double-tap обнаружен - активируем спринт
                isSprinting = true;
                sprintActive.put(uuid, true);
            }
            
            // Запоминаем время нажатия
            lastForwardPress.put(uuid, now);
        }
        
        // Если W отпущена - деактивируем спринт
        if (!isForwardPressed) {
            isSprinting = false;
            sprintActive.put(uuid, false);
        }
        
        // Обновляем состояние нажатия
        wasForwardPressed.put(uuid, isForwardPressed);
        
        return isSprinting;
    }
    
    /**
     * Проверяет, активен ли спринт у игрока.
     */
    public boolean isSprinting(Player player) {
        return sprintActive.getOrDefault(player.getUniqueId(), false);
    }
    
    /**
     * Применяет модификатор безопасного падения к сущности.
     * @param entity сущность (игрок или козёл)
     * @param distance дополнительная дистанция безопасного падения
     */
    public void applySafeFallModifier(LivingEntity entity, double distance) {
        AttributeInstance attribute = entity.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        if (attribute == null) {
            return;
        }
        
        // Удаляем старый модификатор, если есть
        AttributeModifier existing = attribute.getModifier(safeFallModifierKey);
        if (existing != null) {
            attribute.removeModifier(existing);
        }
        
        // Добавляем новый модификатор
        AttributeModifier modifier = new AttributeModifier(
                safeFallModifierKey,
                distance,
                AttributeModifier.Operation.ADD_NUMBER
        );
        attribute.addModifier(modifier);
    }
    
    /**
     * Удаляет модификатор безопасного падения у сущности.
     * @param entity сущность (игрок или козёл)
     */
    public void removeSafeFallModifier(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        if (attribute == null) {
            return;
        }
        
        AttributeModifier existing = attribute.getModifier(safeFallModifierKey);
        if (existing != null) {
            attribute.removeModifier(existing);
        }
    }
    
    /**
     * Удаляет модификатор у козла по UUID.
     * Используется для отложенной очистки через EntityScheduler.
     */
    public void cleanupGoat(Goat goat) {
        UUID goatUuid = goat.getUniqueId();
        if (goatsToCleanup.remove(goatUuid) != null) {
            removeSafeFallModifier(goat);
        }
    }
    
    /**
     * Проверяет, нужно ли очистить козла.
     */
    public boolean needsCleanup(UUID goatUuid) {
        return goatsToCleanup.containsKey(goatUuid);
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
        lastForwardPress.clear();
        wasForwardPressed.clear();
        sprintActive.clear();
        goatsToCleanup.clear();
    }

    /**
     * Получает количество активных наездников.
     */
    public int getRiderCount() {
        return riders.size();
    }
}
