package su.brim.goatrider.listener;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import org.bukkit.event.entity.EntityMountEvent;
import su.brim.goatrider.GoatRiderPlugin;
import su.brim.goatrider.manager.ConfigManager;
import su.brim.goatrider.manager.RidingManager;

/**
 * Слушатель для управления движением козла при езде.
 * Использует EntityScheduler для совместимости с Folia.
 * Использует Player#getCurrentInput() API для получения ввода игрока.
 */
public class GoatControlListener implements Listener {

    private final GoatRiderPlugin plugin;
    private final ConfigManager config;
    private final RidingManager ridingManager;

    public GoatControlListener(GoatRiderPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.ridingManager = plugin.getRidingManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        Entity entity = event.getEntity();
        Entity mount = event.getMount();

        // Проверяем, что игрок сел на козла
        if (!(entity instanceof Player player) || !(mount instanceof Goat goat)) {
            return;
        }

        // Запускаем задачу управления козлом через EntityScheduler
        startControlTask(player, goat);
    }

    /**
     * Запускает периодическую задачу управления козлом.
     * Использует EntityScheduler для корректной работы в Folia.
     */
    private void startControlTask(Player player, Goat goat) {
        ScheduledTask task = goat.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            // Проверяем валидность
            if (!goat.isValid() || !player.isOnline() || !goat.getPassengers().contains(player)) {
                scheduledTask.cancel();
                ridingManager.cancelControlTask(goat.getUniqueId());
                return;
            }

            // Получаем направление взгляда игрока
            Location playerLoc = player.getLocation();
            float yaw = playerLoc.getYaw();

            // Поворачиваем козла в направлении взгляда игрока
            goat.setRotation(yaw, goat.getLocation().getPitch());

            // Получаем ввод игрока через Paper API
            var input = player.getCurrentInput();
            
            // Рассчитываем движение на основе ввода
            Vector movement = calculateMovement(player, goat, input);
            
            // Обрабатываем прыжок
            if (input.isJump()) {
                handleJump(player, goat);
            }

            // Сбрасываем прыжки, если козёл на земле
            if (goat.isOnGround()) {
                ridingManager.resetJumps(player);
            }

            // Применяем движение, если есть ввод
            if (movement.lengthSquared() > 0.001) {
                // Проверяем столкновение для урона тараном
                if (config.isRamEnabled() && input.isForward()) {
                    checkRamCollision(goat, player, movement);
                }
                
                // Сохраняем вертикальную скорость
                movement.setY(goat.getVelocity().getY());
                goat.setVelocity(movement);
            }

        }, null, 1L, 1L);

        ridingManager.setControlTask(goat.getUniqueId(), task);
    }

    /**
     * Рассчитывает вектор движения на основе ввода игрока.
     */
    private Vector calculateMovement(Player player, Goat goat, org.bukkit.Input input) {
        // Получаем направление взгляда игрока (горизонтальное)
        Vector direction = player.getLocation().getDirection();
        direction.setY(0);
        
        if (direction.lengthSquared() > 0) {
            direction.normalize();
        }

        // Получаем вектор вправо
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        Vector movement = new Vector(0, 0, 0);
        double speed = config.getSpeed();

        // Применяем спринт
        if (input.isSprint()) {
            speed *= 1.3;
        }

        // W - вперёд
        if (input.isForward()) {
            movement.add(direction.clone().multiply(speed));
        }
        
        // S - назад (медленнее)
        if (input.isBackward()) {
            movement.add(direction.clone().multiply(-speed * 0.5));
        }
        
        // A - влево
        if (input.isLeft()) {
            movement.add(right.clone().multiply(-speed * 0.7));
        }
        
        // D - вправо
        if (input.isRight()) {
            movement.add(right.clone().multiply(speed * 0.7));
        }

        return movement;
    }

    /**
     * Обрабатывает прыжок козла.
     */
    private void handleJump(Player player, Goat goat) {
        // Проверяем кулдаун прыжка
        if (!ridingManager.canJump(player)) {
            return;
        }
        
        // Проверяем возможность прыжка
        boolean canJump = goat.isOnGround();
        
        // Проверяем мульти-прыжок
        if (!canJump && config.isMultiJump()) {
            canJump = ridingManager.useJump(player);
        }

        if (canJump) {
            Vector velocity = goat.getVelocity();
            velocity.setY(config.getJumpStrength());
            goat.setVelocity(velocity);
        }
    }

    /**
     * Проверяет столкновение для урона тараном.
     */
    private void checkRamCollision(Goat goat, Player rider, Vector movement) {
        // Ищем сущности перед козлом
        Location loc = goat.getLocation();
        Vector direction = movement.clone().normalize();
        
        for (Entity entity : goat.getNearbyEntities(1.5, 1.0, 1.5)) {
            if (entity == rider || entity == goat) {
                continue;
            }
            
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }

            // Проверяем, что сущность перед козлом
            Vector toEntity = entity.getLocation().toVector().subtract(loc.toVector());
            if (toEntity.lengthSquared() > 0 && toEntity.normalize().dot(direction) > 0.5) {
                // Проверяем кулдаун урона для этой цели
                if (!ridingManager.canRamDamage(entity.getUniqueId())) {
                    continue;
                }
                
                // Наносим урон
                target.damage(config.getRamDamage(), goat);
                
                // Отбрасываем
                Vector knockback = direction.clone().multiply(0.5).setY(0.3);
                target.setVelocity(target.getVelocity().add(knockback));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        if (ridingManager.isRiding(player)) {
            ridingManager.removeRider(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Предотвращаем урон козлу от своего наездника
        Entity damager = event.getDamager();
        Entity damaged = event.getEntity();

        if (damaged instanceof Goat goat && damager instanceof Player player) {
            if (goat.getPassengers().contains(player)) {
                event.setCancelled(true);
            }
        }
    }
}
