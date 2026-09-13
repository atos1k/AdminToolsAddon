package dev.atos1k.auc.command.args;

import dev.atos1k.auc.util.CommandUtil;
import dev.by1337.cmd.Argument;
import dev.by1337.cmd.ArgumentMap;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.CommandReader;
import dev.by1337.cmd.SuggestionsList;
import java.util.Locale;

public class ArgumentPlayerName<C> extends Argument<C, String> {
    public ArgumentPlayerName(String name) {
        super(name);
    }

    @Override
    public void parse(C ctx, CommandReader reader, ArgumentMap out) throws CommandMsgError {
        String s = reader.readString();
        if (s.isEmpty()) return;
        out.put(name, s);
    }

    @Override
    public void suggest(C ctx, CommandReader reader, SuggestionsList suggestions, ArgumentMap args) throws CommandMsgError {
        String remaining = suggestions.getRemaining().toLowerCase(Locale.ROOT);
        for (String playerName : CommandUtil.onlinePlayerNames()) {
            if (remaining.isBlank() || playerName.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                suggestions.suggest(playerName);
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
