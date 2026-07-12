package com.soymods.oreveil.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class OreveilScheduler {
    private final Plugin plugin;
    private final Logger logger;
    private final boolean folia;
    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Method globalRun;
    private final Method globalRunDelayed;
    private final Method globalRunAtFixedRate;
    private final Method regionRun;
    private final Method regionRunDelayed;
    private volatile Method entityGetScheduler;
    private volatile Method entityRun;
    private volatile Method entityRunDelayed;

    public OreveilScheduler(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.folia = OreveilRuntime.isFolia();
        FoliaMethods methods = folia ? loadFoliaMethods() : FoliaMethods.empty();
        this.globalScheduler = methods.globalScheduler();
        this.regionScheduler = methods.regionScheduler();
        this.globalRun = methods.globalRun();
        this.globalRunDelayed = methods.globalRunDelayed();
        this.globalRunAtFixedRate = methods.globalRunAtFixedRate();
        this.regionRun = methods.regionRun();
        this.regionRunDelayed = methods.regionRunDelayed();
    }

    public boolean isFolia() {
        return folia;
    }

    public TaskHandle runGlobal(Runnable task) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
            return bukkitTask::cancel;
        }
        return invoke("global run", globalRun, globalScheduler, plugin, taskConsumer(task));
    }

    public TaskHandle runGlobalLater(Runnable task, long delayTicks) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return bukkitTask::cancel;
        }
        return invoke(
            "global runDelayed",
            globalRunDelayed,
            globalScheduler,
            plugin,
            taskConsumer(task),
            normalizeDelay(delayTicks)
        );
    }

    public TaskHandle runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
            return bukkitTask::cancel;
        }
        return invoke(
            "global runAtFixedRate",
            globalRunAtFixedRate,
            globalScheduler,
            plugin,
            taskConsumer(task),
            normalizeDelay(initialDelayTicks),
            normalizeDelay(periodTicks)
        );
    }

    public TaskHandle runAt(Location location, Runnable task) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
            return bukkitTask::cancel;
        }
        return invoke(
            "region run",
            regionRun,
            regionScheduler,
            plugin,
            location,
            taskConsumer(task)
        );
    }

    public TaskHandle runAtLater(Location location, Runnable task, long delayTicks) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return bukkitTask::cancel;
        }
        return invoke(
            "region runDelayed",
            regionRunDelayed,
            regionScheduler,
            plugin,
            location,
            taskConsumer(task),
            normalizeDelay(delayTicks)
        );
    }

    public TaskHandle runFor(Player player, Runnable task) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
            return bukkitTask::cancel;
        }
        return invokeEntity(
            player,
            "run",
            plugin,
            taskConsumer(task),
            noop()
        );
    }

    public TaskHandle runForLater(Player player, Runnable task, long delayTicks) {
        if (!folia) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return bukkitTask::cancel;
        }
        return invokeEntity(
            player,
            "runDelayed",
            plugin,
            taskConsumer(task),
            noop(),
            normalizeDelay(delayTicks)
        );
    }

    private FoliaMethods loadFoliaMethods() {
        try {
            Object loadedGlobalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Object loadedRegionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Class<?> globalClass = loadedGlobalScheduler.getClass();
            Class<?> regionClass = loadedRegionScheduler.getClass();
            return new FoliaMethods(
                loadedGlobalScheduler,
                loadedRegionScheduler,
                globalClass.getMethod("run", Plugin.class, Consumer.class),
                globalClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class),
                globalClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class),
                regionClass.getMethod("run", Plugin.class, Location.class, Consumer.class),
                regionClass.getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class)
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.log(Level.WARNING, "Could not initialize Folia scheduler methods.", exception);
            return FoliaMethods.empty();
        }
    }

    private TaskHandle invoke(String methodName, Method method, Object receiver, Object... args) {
        if (method == null || receiver == null) {
            return failedTask(methodName, new IllegalStateException("Folia scheduler method is unavailable."));
        }
        try {
            return handle(method.invoke(receiver, args));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return failedTask(methodName, exception);
        }
    }

    private TaskHandle invokeEntity(Player player, String methodName, Object... args) {
        try {
            Method schedulerMethod = entityGetScheduler;
            if (schedulerMethod == null) {
                schedulerMethod = player.getClass().getMethod("getScheduler");
                entityGetScheduler = schedulerMethod;
            }
            Object scheduler = schedulerMethod.invoke(player);
            Method method = methodName.equals("run") ? entityRun : entityRunDelayed;
            if (method == null) {
                method = methodName.equals("run")
                    ? scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class)
                    : scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
                if (methodName.equals("run")) {
                    entityRun = method;
                } else {
                    entityRunDelayed = method;
                }
            }
            return handle(method.invoke(scheduler, args));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return failedTask(methodName, exception);
        }
    }

    private TaskHandle failedTask(String methodName, Exception exception) {
        Throwable cause = exception instanceof InvocationTargetException target && target.getCause() != null
            ? target.getCause()
            : exception;
        logger.log(Level.WARNING, "Could not schedule Folia task with " + methodName + ".", cause);
        return () -> {
        };
    }

    private static Consumer<Object> taskConsumer(Runnable task) {
        return ignored -> task.run();
    }

    private static Runnable noop() {
        return () -> {
        };
    }

    private static long normalizeDelay(long delayTicks) {
        return Math.max(1L, delayTicks);
    }

    private static TaskHandle handle(Object scheduledTask) {
        return () -> {
            if (scheduledTask == null) {
                return;
            }
            try {
                Method cancel = scheduledTask.getClass().getMethod("cancel");
                cancel.invoke(scheduledTask);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        };
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }

    private record FoliaMethods(
        Object globalScheduler,
        Object regionScheduler,
        Method globalRun,
        Method globalRunDelayed,
        Method globalRunAtFixedRate,
        Method regionRun,
        Method regionRunDelayed
    ) {
        static FoliaMethods empty() {
            return new FoliaMethods(null, null, null, null, null, null, null);
        }
    }
}
