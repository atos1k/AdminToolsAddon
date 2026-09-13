package dev.atos1k.auc.command.args;

import dev.by1337.cmd.Argument;
import dev.by1337.cmd.ArgumentMap;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.CommandReader;
import dev.by1337.cmd.SuggestionsList;

public class ArgumentPlaceholder<C> extends Argument<C, String> {
    private final String hint;

    public ArgumentPlaceholder(String name, String hint) {
        super(name);
        this.hint = hint;
    }

    @Override
    public void parse(C ctx, CommandReader reader, ArgumentMap out) throws CommandMsgError {
        String s = reader.readString();
        if (s.isEmpty()) return;
        out.put(name, s);
    }

    @Override
    public void suggest(C ctx, CommandReader reader, SuggestionsList suggestions, ArgumentMap args) throws CommandMsgError {
        suggestions.suggest(hint);
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
