package dev.atos1k.auc.command.args;

import dev.by1337.cmd.Argument;
import dev.by1337.cmd.ArgumentMap;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.CommandReader;
import dev.by1337.cmd.SuggestionsList;
import java.util.Locale;
import org.bukkit.Material;

public class ArgumentMaterial<C> extends Argument<C, Material> {
    public ArgumentMaterial(String name) {
        super(name);
    }

    @Override
    public void parse(C ctx, CommandReader reader, ArgumentMap out) throws CommandMsgError {
        String s = reader.readString();
        if (s.isEmpty()) return;
        Material material = Material.matchMaterial(s);
        if (material == null) throw new CommandMsgError("неизвестный материал " + s);
        out.put(name, material);
    }

    @Override
    public void suggest(C ctx, CommandReader reader, SuggestionsList suggestions, ArgumentMap args) throws CommandMsgError {
        String remaining = suggestions.getRemaining().toLowerCase(Locale.ROOT);
        int count = 0;
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isItem()) continue;
            String name = material.name();
            if (remaining.isBlank() || name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                suggestions.suggest(name);
                if (++count >= 40) break;
            }
        }
        reader.readString();
    }

    @Override
    public boolean compilable() {
        return true;
    }

    @Override
    public boolean allowAsync() {
        return true;
    }
}
