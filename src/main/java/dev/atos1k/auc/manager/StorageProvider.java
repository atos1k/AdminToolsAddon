package dev.atos1k.auc.manager;

import dev.atos1k.auc.storage.DatabaseStorage;
import dev.atos1k.auc.storage.Storage;
import dev.atos1k.auc.storage.YamlStorage;
import java.io.File;
import java.util.function.Consumer;

public final class StorageProvider {
    private StorageProvider() {
    }

    public static Storage open(File folder, Consumer<String> logger) {
        try {
            return DatabaseStorage.create();
        } catch (Throwable e) {
            logger.accept("База данных аукциона недоступна, данные админки хранятся в admin-data.yml: " + e);
            return new YamlStorage(folder);
        }
    }
}
