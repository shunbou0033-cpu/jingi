package jp.shinki;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Shinki extends JavaPlugin implements Listener {

    private NamespacedKey artifactKey;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        artifactKey = new NamespacedKey(this, "lightning_artifact");

        if (!getConfig().contains("issued")) {
            getConfig().set("issued", false);
            saveConfig();
        }

        getServer().getPluginManager().registerEvents(this, this);

        // 神器をインベントリに持っている間、イルカの好意を付与
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasArtifactInInventory(player)) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.DOLPHINS_GRACE,
                            80, 0, true, false, true
                    ));
                }
            }
        }, 0L, 40L);

        getLogger().info("Shinki enabled!");
    }

    private boolean isArtifact(ItemStack item) {
        if (item == null || item.getType() == Material.AIR
                || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer()
                .has(artifactKey, PersistentDataType.BYTE);
    }

    private boolean hasArtifactInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isArtifact(item)) return true;
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (isArtifact(item)) return true;
        }
        return isArtifact(player.getInventory().getItemInOffHand());
    }

    private ItemStack createArtifact() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE
                + "" + ChatColor.BOLD + "神器・雷神の剣");
        meta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "右クリック：10%の確率で雷撃",
                ChatColor.AQUA + "所持中：イルカの好意",
                ChatColor.YELLOW + "唯一無二の神器"
        ));

        meta.getPersistentDataContainer().set(
                artifactKey, PersistentDataType.BYTE, (byte) 1
        );

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!sender.hasPermission("shinki.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("holder")) {
            int found = 0;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasArtifactInInventory(player)) {
                    sender.sendMessage(ChatColor.GREEN
                            + "神器の所持者: " + player.getName());
                    found++;
                }
            }

            for (World world : Bukkit.getWorlds()) {
                for (Item dropped : world.getEntitiesByClass(Item.class)) {
                    if (isArtifact(dropped.getItemStack())) {
                        Location loc = dropped.getLocation();
                        sender.sendMessage(ChatColor.YELLOW
                                + "地面: " + world.getName() + " "
                                + loc.getBlockX() + ", "
                                + loc.getBlockY() + ", "
                                + loc.getBlockZ());
                        found++;
                    }
                }
            }

            if (found == 0) {
                if (getConfig().getBoolean("issued", false)) {
                    sender.sendMessage(ChatColor.RED
                            + "発行済みですが、オンライン所持者や"
                            + "地面に神器が見つかりません。");
                } else {
                    sender.sendMessage(ChatColor.GRAY
                            + "神器はまだ発行されていません。");
                }
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (getConfig().getBoolean("issued", false)) {
                sender.sendMessage(ChatColor.RED
                        + "神器は発行済みです。2個目は発行できません。");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED
                        + "対象プレイヤーがオンラインではありません。");
                return true;
            }

            // 先に発行済みとして保存し、再起動後も再発行を防止
            getConfig().set("issued", true);
            saveConfig();

            Map<Integer, ItemStack> leftovers =
                    target.getInventory().addItem(createArtifact());

            leftovers.values().forEach(item ->
                    target.getWorld().dropItemNaturally(
                            target.getLocation(), item));

            sender.sendMessage(ChatColor.GREEN
                    + target.getName() + " に神器を発行しました。");
            target.sendMessage(ChatColor.LIGHT_PURPLE
                    + "神器・雷神の剣を手に入れた！");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== 神器コマンド ===");
        sender.sendMessage(ChatColor.YELLOW
                + "/artifact give <プレイヤー> - 神器を発行");
        sender.sendMessage(ChatColor.YELLOW
                + "/artifact holder - 所持者を確認");
        return true;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!isArtifact(event.getItem())) return;

        // 連打防止：0.5秒に1回まで判定
        long now = System.currentTimeMillis();
        long previous = lastUse.getOrDefault(
                player.getUniqueId(), 0L);
        if (now - previous < 500L) return;
        lastUse.put(player.getUniqueId(), now);

        // 10%の確率で雷撃
        if (random.nextDouble() >= 0.10) {
            player.sendMessage(ChatColor.GRAY
                    + "雷撃は発動しなかった……");
            return;
        }

        Location target;
        var block = player.getTargetBlockExact(25);

        if (block != null) {
            target = block.getLocation().add(0.5, 1.0, 0.5);
        } else {
            target = player.getEyeLocation().add(
                    player.getLocation().getDirection().multiply(10));
        }

        player.getWorld().strikeLightning(target);
        player.sendMessage(ChatColor.LIGHT_PURPLE
                + "⚡ 雷神の剣が発動した！");
    }
}
このコードでは、神器に専用データを付けて通常のネザライト剣と区別している。アイテムの識別にはPaper/BukkitのPersistent Data Containerを使用しているよ。

PaperMC Docs
+1

5. GitHub Actionsを設定する
ファイル名：.github/workflows/build.yml

このファイルがJava 21とMavenを自動で用意し、プラグインをビルドしてダウンロード可能にするよ。

yaml

name: Build Shinki Plugin

on:
  push:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Get source code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build plugin
        run: mvn --batch-mode clean package

      - name: Upload JAR
        uses: actions/upload-artifact@v4
        with:
          name: Shinki-Plugin
          path: target/shinki-1.0.0.jar
          if-no-files-found: error
