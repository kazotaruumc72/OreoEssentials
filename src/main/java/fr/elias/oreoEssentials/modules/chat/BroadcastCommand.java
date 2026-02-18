package fr.elias.oreoEssentials.modules.chat;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BroadcastCommand implements OreoCommand {

    @Override public String name() { return "broadcast"; }
    @Override public List<String> aliases() { return List.of("bc"); }
    @Override public String permission() { return "oreo.broadcast"; }
    @Override public String usage() { return "<message...>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) return false;

        String raw = String.join(" ", args);

        raw = applyPapi(sender, raw);

        String msg = Lang.color(raw);

        String prefix = Lang.msgWithDefault(
                "moderation.broadcast.prefix",
                "<gold>[Broadcast]</gold> ",
                sender instanceof Player ? (Player) sender : null
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(prefix + msg);
        }

        Bukkit.getConsoleSender().sendMessage(prefix +
                PlainTextComponentSerializer.plainText().serialize(Lang.toComponent(raw)));

        return true;
    }


    private String applyPapi(CommandSender sender, String text) {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                if (sender instanceof Player player) {
                    return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
                } else {
                    return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, text);
                }
            }
        } catch (Throwable ignored) {}
        return text;
    }
}