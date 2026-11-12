package com.gawharasmp.missions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class GawharaMissions extends JavaPlugin implements Listener, CommandExecutor {
    private File playersFile;
    private YamlConfiguration playersData;
    private BukkitTask playtimeTask;
    private BukkitTask saveTask;
    private ZoneId zoneId;
    private final Map<UUID, PlayerMissions> cache = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlayerData();
        updateZone();
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("missions");
        if (command != null) command.setExecutor(this);
        startTasks();
        for (Player player : Bukkit.getOnlinePlayers()) ensureCurrent(player.getUniqueId(), player.getName());
        getLogger().info("GawharaMissions enabled. Mission rewards use GawharaCoins (1 GC each by default).");
    }

    @Override
    public void onDisable() {
        if (playtimeTask != null) playtimeTask.cancel();
        if (saveTask != null) saveTask.cancel();
        savePlayerData();
    }


    private void startTasks() {
        // One progress unit is exactly one connected minute.
        long playTicks = 20L * 60L;
        playtimeTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerMissions data = ensureCurrent(player.getUniqueId(), player.getName());
                progress(player, data, MissionType.PLAY_MINUTES, null, null, 1);
            }
        }, playTicks, playTicks);
        long saveTicks = Math.max(20L, getConfig().getLong("settings.autosave-seconds", 60L) * 20L);
        saveTask = Bukkit.getScheduler().runTaskTimer(this, this::savePlayerData, saveTicks, saveTicks);
    }

    private void updateZone() {
        try {
            zoneId = ZoneId.of(getConfig().getString("settings.timezone", "Africa/Cairo"));
        } catch (Exception ex) {
            zoneId = ZoneId.of("Africa/Cairo");
            getLogger().warning("Invalid timezone in config.yml; using Africa/Cairo.");
        }
    }

    private void loadPlayerData() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }
        playersFile = new File(getDataFolder(), "players.yml");
        playersData = YamlConfiguration.loadConfiguration(playersFile);
    }

    private synchronized void savePlayerData() {
        if (playersData == null || playersFile == null) return;
        for (Map.Entry<UUID, PlayerMissions> entry : cache.entrySet()) {
            String root = "players." + entry.getKey();
            PlayerMissions data = entry.getValue();
            playersData.set(root + ".name", data.name);
            playersData.set(root + ".daily-period", data.dailyPeriod);
            playersData.set(root + ".weekly-period", data.weeklyPeriod);
            playersData.set(root + ".daily-ids", data.dailyIds);
            playersData.set(root + ".weekly-ids", data.weeklyIds);
            saveProgress(root + ".daily", data.daily);
            saveProgress(root + ".weekly", data.weekly);
        }
        try {
            playersData.save(playersFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save players.yml", ex);
        }
    }

    private void saveProgress(String path, Map<String, Integer> progress) {
        playersData.set(path, null);
        for (Map.Entry<String, Integer> entry : progress.entrySet()) {
            playersData.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    private PlayerMissions ensureCurrent(UUID uuid, String name) {
        String dailyPeriod = LocalDate.now(zoneId).toString();
        LocalDate today = LocalDate.now(zoneId);
        WeekFields wf = WeekFields.ISO;
        String weeklyPeriod = today.get(wf.weekBasedYear()) + "-W" + String.format(Locale.ROOT, "%02d", today.get(wf.weekOfWeekBasedYear()));
        PlayerMissions data = cache.get(uuid);
        if (data == null) {
            data = new PlayerMissions();
            String root = "players." + uuid;
            data.name = playersData.getString(root + ".name", name);
            data.dailyPeriod = playersData.getString(root + ".daily-period", "");
            data.weeklyPeriod = playersData.getString(root + ".weekly-period", "");
            data.daily.putAll(readProgress(root + ".daily"));
            data.weekly.putAll(readProgress(root + ".weekly"));
            data.dailyIds.addAll(playersData.getStringList(root + ".daily-ids"));
            data.weeklyIds.addAll(playersData.getStringList(root + ".weekly-ids"));
            cache.put(uuid, data);
        }
        data.name = name;
        if (!dailyPeriod.equals(data.dailyPeriod)) {
            data.dailyPeriod = dailyPeriod;
            data.daily.clear();
            data.dailyIds.clear();
            assignMissions(data, "daily");
        } else if (data.dailyIds.isEmpty()) {
            assignMissions(data, "daily");
        }
        if (!weeklyPeriod.equals(data.weeklyPeriod)) {
            data.weeklyPeriod = weeklyPeriod;
            data.weekly.clear();
            data.weeklyIds.clear();
            assignMissions(data, "weekly");
        } else if (data.weeklyIds.isEmpty()) {
            assignMissions(data, "weekly");
        }
        return data;
    }

    private Map<String, Integer> readProgress(String path) {
        Map<String, Integer> result = new HashMap<>();
        ConfigurationSection section = playersData.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) result.put(key, section.getInt(key));
        }
        return result;
    }

    private void assignMissions(PlayerMissions data, String period) {
        List<Mission> pool = loadMissions(period);
        Collections.shuffle(pool, random);
        int count = Math.max(1, getConfig().getInt("settings." + period + "-missions-per-player", 3));
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            data.missionIds(period).add(pool.get(i).id);
            data.progress(period).putIfAbsent(pool.get(i).id, 0);
        }
        if (pool.isEmpty()) getLogger().warning("No valid " + period + " missions were found in config.yml.");
    }

    private List<Mission> loadMissions(String period) {
        List<Mission> result = new ArrayList<>();
        String key = period + "-missions-per-player-pool";
        for (Map<?, ?> raw : getConfig().getMapList(key)) {
            try {
                Mission mission = Mission.fromMap(raw);
                if (mission.target > 0 && mission.reward >= 0) result.add(mission);
            } catch (Exception ex) {
                getLogger().warning("Skipping invalid mission in " + key + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private void progress(Player player, PlayerMissions data, MissionType type, Material material, EntityType entityType, int amount) {
        if (amount <= 0) return;
        if (getConfig().getStringList("settings.disabled-worlds").contains(player.getWorld().getName())) return;
        if (getConfig().getStringList("settings.ignored-game-modes").contains(player.getGameMode().name())) return;
        for (String period : List.of("daily", "weekly")) {
            for (Mission mission : getAssignedMissions(data, period)) {
                if (mission.type != type || !mission.matches(material, entityType)) continue;
                Map<String, Integer> progress = data.progress(period);
                int old = progress.getOrDefault(mission.id, 0);
                if (old >= mission.target) continue;
                int updated = Math.min(mission.target, old + amount);
                progress.put(mission.id, updated);
                if (updated >= mission.target && !completeMission(player, mission, period)) {
                    progress.put(mission.id, mission.target - 1);
                }
            }
        }
    }

    private List<Mission> getAssignedMissions(PlayerMissions data, String period) {
        List<Mission> result = new ArrayList<>();
        for (Mission mission : loadMissions(period)) {
            if (data.missionIds(period).contains(mission.id)) result.add(mission);
        }
        return result;
    }

    private boolean completeMission(Player player, Mission mission, String period) {
        long coinReward = Math.max(1L, getConfig().getLong("coins.per-mission", 1L));
        String transactionKey = "mission:" + player.getUniqueId() + ":" + period + ":" + mission.id;
        if (!awardCoinsOnce(player.getUniqueId(), coinReward, "Mission completed: " + mission.id, transactionKey)) {
            getLogger().warning("Could not award " + coinReward + " GC to " + player.getName() + " for mission " + mission.id);
            player.sendMessage(color("&cMission completed, but the GC reward could not be saved. Please contact staff."));
            return false;
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("title", plain(mission.title));
        vars.put("reward", coinReward + " GC");
        vars.put("period", period);
        send(player, "completed", vars);
        playSound(player, "complete");
        if (getConfig().getBoolean("settings.announce-completion", false)) {
            String message = getConfig().getString("messages.broadcast-completed", "&b{player} &7completed &f{title}");
            Bukkit.broadcastMessage(color(message.replace("{player}", player.getName()).replace("{title}", plain(mission.title))));
        }
        savePlayerData();
        return true;
    }

    private String plain(String text) {
        return ChatColor.stripColor(color(text));
    }

    private void send(CommandSender sender, String key, Map<String, String> vars) {
        String message = getConfig().getString("messages." + key, "");
        for (Map.Entry<String, String> entry : vars.entrySet()) message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        sender.sendMessage(color(getConfig().getString("messages.prefix", "") + message));
    }

    private void playSound(Player player, String key) {
        String name = getConfig().getString("settings.sounds." + key, "");
        if (name == null || name.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound,
                    (float) getConfig().getDouble("settings.sounds.volume", 0.8),
                    (float) getConfig().getDouble("settings.sounds.pitch", 1.2));
        } catch (IllegalArgumentException ignored) { }
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private boolean awardCoinsOnce(UUID playerId, long amount, String reason, String transactionKey) {
        var coins = Bukkit.getPluginManager().getPlugin("GawharaCoins");
        if (coins == null || !coins.isEnabled()) {
            getLogger().warning("GawharaCoins is not installed/enabled; cannot award mission GC.");
            return false;
        }
        try {
            Object result = coins.getClass().getMethod("awardCoinsOnce", UUID.class, long.class, String.class, String.class)
                    .invoke(coins, playerId, amount, reason, transactionKey);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "GawharaCoins API call failed", ex);
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("gawharamissions.admin")) {
                sender.sendMessage(color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages.no-permission", "&cNo permission.")));
                return true;
            }
            reloadConfig();
            updateZone();
            if (playtimeTask != null) playtimeTask.cancel();
            if (saveTask != null) saveTask.cancel();
            startTasks();
            sender.sendMessage(color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages.reloaded", "&aReloaded.")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        if (!player.hasPermission("gawharamissions.use")) {
            player.sendMessage(color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages.no-permission", "&cNo permission.")));
            return true;
        }
        String period = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "all";
        if (!List.of("all", "daily", "weekly").contains(period)) {
            player.sendMessage(color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages.usage", "&e/missions [daily|weekly|reload]")));
            return true;
        }
        openMissionGui(player, period);
        return true;
    }

    private void openMissionGui(Player player, String filter) {
        PlayerMissions data = ensureCurrent(player.getUniqueId(), player.getName());
        int size = getConfig().getInt("settings.gui-size", 54);
        if (size != 27 && size != 36 && size != 45 && size != 54) size = 54;
        String title = color(getConfig().getString("settings.gui-title", "&8✦ &bGawhara &fMissions"));
        MissionHolder holder = new MissionHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        int slot = 10;
        if (filter.equals("all") || filter.equals("daily")) {
            slot = addSection(inventory, data, "daily", slot, size);
        }
        if (filter.equals("all") || filter.equals("weekly")) {
            addSection(inventory, data, "weekly", Math.max(slot + 1, 28), size);
        }
        ItemStack filler = item(Material.BLUE_STAINED_GLASS_PANE, "&8 ", List.of());
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null && (i < 9 || i >= size - 9)) inventory.setItem(i, filler);
        }
        player.openInventory(inventory);
        playSound(player, "open");
    }

    private int addSection(Inventory inventory, PlayerMissions data, String period, int start, int size) {
        String header = period.equals("daily")
                ? getConfig().getString("messages.daily-header", "&b&lDAILY MISSIONS")
                : getConfig().getString("messages.weekly-header", "&3&lWEEKLY MISSIONS");
        int headerSlot = period.equals("daily") ? 4 : 22;
        if (headerSlot < size) inventory.setItem(headerSlot, item(period.equals("daily") ? Material.SUNFLOWER : Material.CLOCK, header, List.of("&7Your personal " + period + " missions")));
        int slot = start;
        for (Mission mission : getAssignedMissions(data, period)) {
            if (slot >= size - 9) break;
            int value = data.progress(period).getOrDefault(mission.id, 0);
            boolean complete = value >= mission.target;
            List<String> lore = new ArrayList<>();
            lore.add(color(mission.description));
            lore.add(" ");
            lore.add(color("&7Progress: &f" + value + "&7/&f" + mission.target));
            lore.add(progressBar(value, mission.target));
            lore.add(color("&7Reward: &e" + getConfig().getLong("coins.per-mission", 1L) + " GC"));
            lore.add(" ");
            lore.add(complete ? color("&a✔ Completed") : color("&eIn progress"));
            Material icon = mission.icon == null ? (complete ? Material.LIME_DYE : Material.PAPER) : mission.icon;
            inventory.setItem(slot++, item(icon, mission.title, lore));
        }
        return slot;
    }

    private String progressBar(int progress, int target) {
        int filled = target <= 0 ? 0 : Math.max(0, Math.min(10, (int) Math.floor((progress / (double) target) * 10.0)));
        return color("&b[" + "&3".repeat(filled) + "&8".repeat(10 - filled) + "&b]");
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) colored.add(color(line));
            meta.setLore(colored);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerMissions data = ensureCurrent(player.getUniqueId(), player.getName());
        progress(player, data, MissionType.BREAK_BLOCK, event.getBlock().getType(), null, 1);
        if (event.getBlock().getBlockData() instanceof Ageable crop && crop.getAge() >= crop.getMaximumAge()) {
            progress(player, data, MissionType.HARVEST_CROP, event.getBlock().getType(), null, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) return;
        progress(killer, ensureCurrent(killer.getUniqueId(), killer.getName()), MissionType.KILL_MOB,
                null, event.getEntity().getType(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) return;
        Player player = event.getPlayer();
        progress(player, ensureCurrent(player.getUniqueId(), player.getName()), MissionType.FISH,
                item.getItemStack().getType(), null, item.getItemStack().getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getRecipe().getResult();
        int amount = Math.max(1, result.getAmount());
        progress(player, ensureCurrent(player.getUniqueId(), player.getName()), MissionType.CRAFT_ITEM,
                result.getType(), null, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        progress(player, ensureCurrent(player.getUniqueId(), player.getName()), MissionType.SMELT_ITEM,
                event.getItemType(), null, event.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        progress(player, ensureCurrent(player.getUniqueId(), player.getName()), MissionType.BREED_ANIMAL,
                null, event.getEntity().getType(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMissionInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MissionHolder) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMissionInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MissionHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ensureCurrent(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerMissions data = cache.get(event.getPlayer().getUniqueId());
        if (data != null) data.name = event.getPlayer().getName();
        savePlayerData();
    }

    private enum MissionType {
        BREAK_BLOCK, HARVEST_CROP, KILL_MOB, FISH, CRAFT_ITEM, SMELT_ITEM, BREED_ANIMAL, PLAY_MINUTES
    }

    private static final class Mission {
        private final String id;
        private final String title;
        private final String description;
        private final MissionType type;
        private final int target;
        private final double reward;
        private final Set<Material> materials;
        private final Set<EntityType> entities;
        private final String entityCategory;
        private final Material icon;

        private Mission(String id, String title, String description, MissionType type, int target, double reward,
                        Set<Material> materials, Set<EntityType> entities, String entityCategory, Material icon) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.type = type;
            this.target = target;
            this.reward = reward;
            this.materials = materials;
            this.entities = entities;
            this.entityCategory = entityCategory;
            this.icon = icon;
        }

        @SuppressWarnings("unchecked")
        static Mission fromMap(Map<?, ?> map) {
            String id = String.valueOf(map.get("id"));
            String title = String.valueOf(map.containsKey("title") ? map.get("title") : id);
            String description = String.valueOf(map.containsKey("description") ? map.get("description") : "");
            MissionType type = MissionType.valueOf(String.valueOf(map.get("type")).toUpperCase(Locale.ROOT));
            int target = Integer.parseInt(String.valueOf(map.get("target")));
            double reward = Double.parseDouble(String.valueOf(map.containsKey("reward") ? map.get("reward") : 0));
            Set<Material> materials = EnumSet.noneOf(Material.class);
            Object material = map.get("material");
            if (material != null) addEnum(materials, Material.class, material);
            Object materialsObject = map.get("materials");
            if (materialsObject instanceof List<?> list) for (Object value : list) addEnum(materials, Material.class, value);
            Set<EntityType> entities = EnumSet.noneOf(EntityType.class);
            Object entity = map.get("entity");
            if (entity != null) addEnum(entities, EntityType.class, entity);
            String category = String.valueOf(map.containsKey("entity-category") ? map.get("entity-category") : "");
            if (!category.isBlank() && !category.equalsIgnoreCase("MONSTER")) {
                throw new IllegalArgumentException("Unsupported entity-category: " + category);
            }
            Material icon = null;
            Object iconValue = map.get("icon");
            if (iconValue != null) {
                try { icon = Material.valueOf(String.valueOf(iconValue).toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ignored) { }
            }
            return new Mission(id, title, description, type, target, reward, materials, entities, category, icon);
        }

        private static <E extends Enum<E>> void addEnum(Set<E> set, Class<E> type, Object value) {
            set.add(Enum.valueOf(type, String.valueOf(value).toUpperCase(Locale.ROOT)));
        }

        boolean matches(Material material, EntityType entityType) {
            if (!materials.isEmpty() && (material == null || !materials.contains(material))) return false;
            if (!entities.isEmpty() && (entityType == null || !entities.contains(entityType))) return false;
            if (!entityCategory.isBlank()) {
                if (entityType == null) return false;
                if (entityCategory.equalsIgnoreCase("MONSTER")) {
                    Class<?> clazz = entityType.getEntityClass();
                    if (clazz == null || !org.bukkit.entity.Monster.class.isAssignableFrom(clazz)) return false;
                }
            }
            return true;
        }
    }

    private static final class PlayerMissions {
        private String name = "Unknown";
        private String dailyPeriod = "";
        private String weeklyPeriod = "";
        private final Map<String, Integer> daily = new LinkedHashMap<>();
        private final Map<String, Integer> weekly = new LinkedHashMap<>();
        private final List<String> dailyIds = new ArrayList<>();
        private final List<String> weeklyIds = new ArrayList<>();
        Map<String, Integer> progress(String period) { return period.equals("daily") ? daily : weekly; }
        List<String> missionIds(String period) { return period.equals("daily") ? dailyIds : weeklyIds; }
    }

    private static final class MissionHolder implements InventoryHolder {
        private Inventory inventory;
        void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
