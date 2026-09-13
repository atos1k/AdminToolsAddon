package dev.atos1k.auc.storage;

import dev.atos1k.auc.util.BanTypes;
import dev.by1337.auc.BAuction;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;

public final class DatabaseStorage implements Storage {
    private static final String BANS = "bauction_admin_bans";
    private static final String BLACKLIST = "bauction_admin_blacklist";

    private final DataSource dataSource;
    private final Object databaseSource;

    private DatabaseStorage(DataSource dataSource, Object databaseSource) {
        this.dataSource = dataSource;
        this.databaseSource = databaseSource;
    }

    public static DatabaseStorage create() throws Exception {
        Object dbConfig = BAuction.plugin().config().dbConfig.database;
        Class<?> sourceClass = Class.forName("dev.by1337.sync.bd.DatabaseSource");

        Constructor<?> constructor = null;
        for (Constructor<?> c : sourceClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 2 && params[0].isInstance(dbConfig) && params[1] == String.class) {
                constructor = c;
                break;
            }
        }
        if (constructor == null) throw new IllegalStateException("DatabaseSource constructor not found");
        constructor.setAccessible(true);
        Object databaseSource = constructor.newInstance(dbConfig, "./bsync");

        Method accessor = sourceClass.getMethod("dataSource");
        accessor.setAccessible(true);
        DataSource dataSource = (DataSource) accessor.invoke(databaseSource);

        DatabaseStorage storage = new DatabaseStorage(dataSource, databaseSource);
        storage.createTables();
        return storage;
    }

    private void createTables() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS `%s` (
                        uuid BINARY(16) NOT NULL,
                        ban_type VARCHAR(16) NOT NULL,
                        name VARCHAR(32),
                        created BIGINT NOT NULL,
                        expires BIGINT NOT NULL DEFAULT 0,
                        reason VARCHAR(255),
                        PRIMARY KEY (uuid, ban_type)
                    )""".formatted(BANS));
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS `%s` (
                        material VARCHAR(64) NOT NULL,
                        added_by VARCHAR(32),
                        created BIGINT NOT NULL,
                        PRIMARY KEY (material)
                    )""".formatted(BLACKLIST));
        }
        alterQuietly("ALTER TABLE `%s` ADD COLUMN expires BIGINT NOT NULL DEFAULT 0".formatted(BANS));
        alterQuietly("ALTER TABLE `%s` ADD COLUMN reason VARCHAR(255)".formatted(BANS));
    }

    private void alterQuietly(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<BanEntry> loadBans() throws Exception {
        String sql = "SELECT `uuid`, `ban_type`, `name`, `created`, `expires`, `reason` FROM `%s`".formatted(BANS);
        List<BanEntry> out = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID uuid = bytesToUuid(rs.getBytes(1));
                BanTypes type = BanTypes.byId(rs.getString(2));
                if (uuid == null || type == null) continue;
                out.add(new BanEntry(uuid, rs.getString(3), type, rs.getLong(4), rs.getLong(5), rs.getString(6)));
            }
        }
        return out;
    }

    @Override
    public void putBan(BanEntry entry) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM `%s` WHERE `uuid` = ? AND `ban_type` = ?".formatted(BANS))) {
                delete.setBytes(1, uuidToBytes(entry.uuid()));
                delete.setString(2, entry.type().id());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    ("INSERT INTO `%s` (`uuid`, `ban_type`, `name`, `created`, `expires`, `reason`) "
                            + "VALUES (?, ?, ?, ?, ?, ?)").formatted(BANS))) {
                insert.setBytes(1, uuidToBytes(entry.uuid()));
                insert.setString(2, entry.type().id());
                insert.setString(3, entry.name());
                insert.setLong(4, entry.created());
                insert.setLong(5, entry.expires());
                insert.setString(6, entry.reason());
                insert.executeUpdate();
            }
        }
    }

    @Override
    public void removeBan(UUID uuid, BanTypes type) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM `%s` WHERE `uuid` = ? AND `ban_type` = ?".formatted(BANS))) {
            statement.setBytes(1, uuidToBytes(uuid));
            statement.setString(2, type.id());
            statement.executeUpdate();
        }
    }

    @Override
    public void removeBans(UUID uuid) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM `%s` WHERE `uuid` = ?".formatted(BANS))) {
            statement.setBytes(1, uuidToBytes(uuid));
            statement.executeUpdate();
        }
    }

    @Override
    public List<String> loadBlacklist() throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `material` FROM `%s`".formatted(BLACKLIST));
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1).toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    @Override
    public void addBlacklist(String material, String addedBy) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM `%s` WHERE `material` = ?".formatted(BLACKLIST))) {
                delete.setString(1, material);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO `%s` (`material`, `added_by`, `created`) VALUES (?, ?, ?)".formatted(BLACKLIST))) {
                insert.setString(1, material);
                insert.setString(2, addedBy);
                insert.setLong(3, System.currentTimeMillis());
                insert.executeUpdate();
            }
        }
    }

    @Override
    public void removeBlacklist(String material) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM `%s` WHERE `material` = ?".formatted(BLACKLIST))) {
            statement.setString(1, material);
            statement.executeUpdate();
        }
    }

    @Override
    public void close() {
        try {
            Method close = databaseSource.getClass().getMethod("close");
            close.invoke(databaseSource);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID bytesToUuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
