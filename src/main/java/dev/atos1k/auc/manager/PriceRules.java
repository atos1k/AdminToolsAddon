package dev.atos1k.auc.manager;

import dev.atos1k.auc.util.BYaml;
import dev.by1337.yaml.YamlMap;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;

public final class PriceRules {
    public record Rule(long minCents, long maxCents) {
    }

    public record Violation(boolean tooLow, long limitCents, String material) {
    }

    private final File file;
    private final Consumer<String> warn;
    private final Map<String, Rule> rules = new ConcurrentHashMap<>();

    public PriceRules(File folder, Consumer<String> warn) {
        this.file = new File(folder, "pricerules.yml");
        this.warn = warn;
    }

    public void load() {
        rules.clear();
        YamlMap yaml = BYaml.read(file);
        for (Map.Entry<String, Object> entry : BYaml.section(yaml, "rules").entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rule)) continue;
            rules.put(entry.getKey().toUpperCase(Locale.ROOT),
                    new Rule(BYaml.number(rule.get("min"), 0L), BYaml.number(rule.get("max"), 0L)));
        }
    }

    private synchronized void save() {
        YamlMap yaml = new YamlMap();
        for (Map.Entry<String, Rule> entry : new TreeMap<>(rules).entrySet()) {
            yaml.set("rules." + entry.getKey() + ".min", entry.getValue().minCents());
            yaml.set("rules." + entry.getKey() + ".max", entry.getValue().maxCents());
        }
        try {
            BYaml.write(file, yaml);
        } catch (IOException e) {
            warn.accept("Не удалось сохранить pricerules.yml: " + e.getMessage());
        }
    }

    public void set(String material, long minCents, long maxCents) {
        rules.put(material.toUpperCase(Locale.ROOT), new Rule(minCents, maxCents));
        save();
    }

    public boolean remove(String material) {
        boolean removed = rules.remove(material.toUpperCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    public Map<String, Rule> all() {
        return new TreeMap<>(rules);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public Violation check(ItemStack stack, long centsPrice, int count) {
        if (rules.isEmpty() || stack == null) return null;
        String material = stack.getType().name();
        Rule rule = rules.get(material);
        if (rule == null) return null;
        long unit = centsPrice / Math.max(1, count);
        if (rule.minCents() > 0L && unit < rule.minCents()) return new Violation(true, rule.minCents(), material);
        if (rule.maxCents() > 0L && unit > rule.maxCents()) return new Violation(false, rule.maxCents(), material);
        return null;
    }
}
