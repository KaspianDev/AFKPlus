/*
 * Copyright 2017 Benjamin Martin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lapismc.afkplus;

import net.lapismc.afkplus.commands.AFKPlusAFK;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

public final class AFKPlus extends JavaPlugin {

    public HashMap<UUID, Long> timeSinceLastInteract = new HashMap<>();
    public HashMap<UUID, Boolean> commandAFK = new HashMap<>();
    public ArrayList<UUID> warnedPlayers = new ArrayList<>();
    public HashMap<UUID, Long> playersAFK = new HashMap<>();
    public Logger logger = Bukkit.getLogger();
    public AFKPlusListeners AFKListeners;
    public AFKPlusConfiguration AFKConfig;
    public LapisUpdater updater;
    Integer timer;

    @Override
    public void onEnable() {
        this.getCommand("afkplus").setExecutor(new net.lapismc.afkplus.commands.AFKPlus(this));
        this.getCommand("afk").setExecutor(new AFKPlusAFK(this));
        saveDefaultConfig();
        update();
        configVersion();
        Metrics metrics = new Metrics(this);
        metrics.start();
        AFKListeners = new AFKPlusListeners(this);
        AFKConfig = new AFKPlusConfiguration(this);
        Bukkit.getPluginManager().registerEvents(AFKListeners, this);
        startTimer();
    }

    private void configVersion() {
        if (getConfig().getInt("ConfigVersion") != 2) {
            File oldConfig = new File(getDataFolder() + File.separator + "config.yml");
            File backupConfig = new File(getDataFolder() + File.separator +
                    "Backup_config.yml");
            oldConfig.renameTo(backupConfig);
            saveDefaultConfig();
            logger.info("New config generated!");
            logger.info("Please transfer values!");
        }
    }

    private void update() {
        updater = new LapisUpdater(this, "AFKPlus", "Dart2112", "AFKPlus", "master");
        if (updater.checkUpdate("AFKPlus")) {
            if (getConfig().getBoolean("UpdateDownload")) {
                updater.downloadUpdate("AFKPlus");
            } else {
                logger.info("A new update is available for AFKPlus");
            }
        } else {
            logger.info("No update available for AFKPlus");
        }
    }

    private Runnable runnable(AFKPlus plugin) {
        return new Runnable() {
            @Override
            public void run() {
                Date date = new Date();
                for (UUID uuid : timeSinceLastInteract.keySet()) {
                    Long time = timeSinceLastInteract.get(uuid);
                    Long difference = (date.getTime() - time) / 1000;
                    if (difference.intValue() >= getConfig().getInt("TimeUntilAFK")) {
                        startAFK(uuid, false);
                    }
                }
                for (UUID uuid : playersAFK.keySet()) {
                    if (!Bukkit.getPlayer(uuid).hasPermission("afkplus.admin")) {
                        Long time = playersAFK.get(uuid);
                        Long difference = (date.getTime() - time) / 1000;
                        if (difference.intValue() >= getConfig().getInt("TimeUntilWarn")) {
                            Player p = Bukkit.getPlayer(uuid);
                            p.sendMessage(AFKConfig.getColoredMessage("WarnMessage"));
                            warnedPlayers.add(uuid);
                            p.playNote(p.getLocation(), Instrument.PIANO, Note.flat(1, Note.Tone.C));
                            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
                                @Override
                                public void run() {
                                    p.playNote(p.getLocation(), Instrument.PIANO, Note.flat(1, Note.Tone.F));
                                }
                            }, 2);
                        }
                        if (difference.intValue() >= getConfig().getInt("TimeUntilAction")) {
                            Player p = Bukkit.getPlayer(uuid);
                            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
                                @Override
                                public void run() {
                                    playersAFK.remove(uuid);
                                }
                            }, 2);
                            switch (getConfig().getString("Action")) {
                                case "COMMAND":
                                    if (getConfig().getString("ActionVariable").contains(":")) {
                                        for (String s : getConfig().getString("ActionVariable").split(":")) {
                                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                                    s.replace("%NAME", p.getName()));
                                        }
                                    } else {
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                                getConfig().getString("ActionVariable").replace("%NAME", p.getName()));
                                    }
                                    break;
                                case "MESSAGE":
                                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', ChatColor.translateAlternateColorCodes('&',
                                            getConfig().getString("ActionVariable").replace("%NAME", p.getName()))));
                                    break;
                                case "KICK":
                                    p.kickPlayer(ChatColor.translateAlternateColorCodes('&', ChatColor.translateAlternateColorCodes('&',
                                            getConfig().getString("ActionVariable").replace("%NAME", p.getName()))));
                                    break;
                                default:
                                    logger.severe("The AFK+ action is not correctly set in the config!");
                            }
                        }
                    }
                }
            }
        };
    }

    public void startTimer() {
        timer = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, runnable(this), 20, 20);
    }

    public void startAFK(UUID uuid, Boolean command) {
        if (!playersAFK.containsKey(uuid)) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    Date date = new Date();
                    commandAFK.put(uuid, command);
                    playersAFK.put(uuid, date.getTime());
                    timeSinceLastInteract.remove(uuid);
                    Bukkit.broadcastMessage(AFKConfig.getColoredMessage("AFKStart")
                            .replace("%NAME", Bukkit.getPlayer(uuid).getName()));
                }
            }, 2);
        }
    }

    public void stopAFK(UUID uuid) {
        if (playersAFK.containsKey(uuid)) {
            if (warnedPlayers.contains(uuid)) {
                warnedPlayers.remove(uuid);
            }
            Date date = new Date();
            playersAFK.remove(uuid);
            commandAFK.remove(uuid);
            timeSinceLastInteract.put(uuid, date.getTime());
            Bukkit.broadcastMessage(AFKConfig.getColoredMessage("AFKStop").replace("%NAME", Bukkit.getPlayer(uuid).getName()));
        }
    }
}
