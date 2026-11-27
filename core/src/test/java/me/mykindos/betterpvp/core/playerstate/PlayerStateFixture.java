package me.mykindos.betterpvp.core.playerstate;

import com.google.inject.Guice;
import com.google.inject.Provider;
import lombok.Getter;
import me.mykindos.betterpvp.core.database.AsyncDSLContext;
import me.mykindos.betterpvp.core.database.Database;
import me.mykindos.betterpvp.core.database.connection.IDatabaseConnection;
import me.mykindos.betterpvp.core.effects.EffectManager;
import me.mykindos.betterpvp.core.item.BaseItem;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemGroup;
import me.mykindos.betterpvp.core.item.ItemInstance;
import me.mykindos.betterpvp.core.item.ItemRarity;
import me.mykindos.betterpvp.core.item.ItemRegistry;
import me.mykindos.betterpvp.core.item.component.impl.durability.DurabilityComponent;
import me.mykindos.betterpvp.core.item.component.impl.durability.DurabilityComponentSerializer;
import me.mykindos.betterpvp.core.item.component.impl.uuid.UUIDProperty;
import me.mykindos.betterpvp.core.item.component.serialization.ComponentSerializationRegistry;
import me.mykindos.betterpvp.core.item.component.serialization.impl.UUIDPropertySerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.flywaydb.core.Flyway;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A real Postgres with the player state table, and a store, capture and item factory over it. Build one after
 * MockBukkit is mocked. Item comparisons go through the item encoding because MockBukkit compares nested byte arrays
 * by reference.
 */
@Getter
final class PlayerStateFixture {

    private static final NamespacedKey BLADE = new NamespacedKey("test", "blade");

    private static GenericContainer<?> postgres;
    private static DataSource dataSource;

    private final ItemFactory itemFactory;
    private final ItemDataCodec itemCodec;
    private final DatabasePlayerStateStore store;
    private final PlayerStateCapture capture;

    PlayerStateFixture() {
        final ComponentSerializationRegistry serializers = new ComponentSerializationRegistry();
        serializers.register(new UUIDPropertySerializer());
        serializers.register(new DurabilityComponentSerializer());
        final BaseItem blade = new BaseItem("Blade", new ItemStack(Material.DIAMOND_SWORD), ItemGroup.WEAPON, ItemRarity.LEGENDARY);
        final ItemRegistry registry = mock(ItemRegistry.class);
        when(registry.getItem(BLADE)).thenReturn(blade);
        when(registry.getKey(blade)).thenReturn(BLADE);
        itemFactory = Guice.createInjector(binder -> {
            binder.bind(ItemRegistry.class).toProvider((Provider<ItemRegistry>) () -> registry);
            binder.bind(ComponentSerializationRegistry.class).toProvider((Provider<ComponentSerializationRegistry>) () -> serializers);
        }).getInstance(ItemFactory.class);

        final IDatabaseConnection connection = new IDatabaseConnection() {
            @Override
            public void runDatabaseMigrations(ClassLoader classLoader, String location, String name) {
            }

            @Override
            public DataSource getDataSource() {
                return dataSource;
            }
        };
        final Database database = mock(Database.class);
        when(database.getAsyncDslContext()).thenReturn(new AsyncDSLContext(connection, Runnable::run));
        itemCodec = new ItemDataCodec(itemFactory);
        store = new DatabasePlayerStateStore(database, new PlayerStateCodec(itemCodec));
        capture = new PlayerStateCapture(mock(EffectManager.class));
    }

    /** Skips the calling test class when Docker is not running. */
    static void startPostgres() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not running");
        postgres = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withEnv("POSTGRES_PASSWORD", "test")
                .withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
        postgres.start();
        final PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl("jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/postgres");
        source.setUser("postgres");
        source.setPassword("test");
        dataSource = source;
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:core-migrations/postgres/playerstate")
                .load()
                .migrate();
    }

    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    /** A custom item with a fresh uuid, the given durability damage and nested persistent data of several types. */
    ItemStack blade(int damage) {
        final DurabilityComponent durability = new DurabilityComponent(100);
        durability.setDamage(damage);
        final ItemInstance instance = itemFactory.create(itemFactory.getItemRegistry().getItem(BLADE))
                .withComponent(new UUIDProperty())
                .withComponent(durability);
        final ItemStack stack = instance.createItemStack();
        stack.editPersistentDataContainer(pdc -> {
            final PersistentDataContainer nested = pdc.getAdapterContext().newPersistentDataContainer();
            nested.set(new NamespacedKey("test", "long"), PersistentDataType.LONG, Long.MAX_VALUE - damage);
            nested.set(new NamespacedKey("test", "bytes"), PersistentDataType.BYTE_ARRAY, new byte[]{1, 2, (byte) damage});
            nested.set(new NamespacedKey("test", "flag"), PersistentDataType.BOOLEAN, true);
            nested.set(new NamespacedKey("test", "ratio"), PersistentDataType.DOUBLE, damage / 3.0);
            pdc.set(new NamespacedKey("test", "extra"), PersistentDataType.TAG_CONTAINER, nested);
        });
        return stack;
    }

    void assertSameItem(@Nullable ItemStack expected, @Nullable ItemStack actual) {
        assertEquals(expected == null, actual == null);
        if (expected == null) {
            return;
        }
        assertEquals(itemCodec.encode(expected), itemCodec.encode(actual));
        final ItemInstance before = itemFactory.fromItemStack(expected).orElseThrow();
        final ItemInstance after = itemFactory.fromItemStack(actual).orElseThrow();
        assertEquals(itemFactory.getItemRegistry().getKey(before.getBaseItem()), itemFactory.getItemRegistry().getKey(after.getBaseItem()));
        assertEquals(uuid(before), uuid(after));
        assertEquals(damage(before), damage(after));
    }

    @Nullable UUID uuid(@NotNull ItemStack stack) {
        return uuid(itemFactory.fromItemStack(stack).orElseThrow());
    }

    private static UUID uuid(ItemInstance instance) {
        return instance.getComponent(UUIDProperty.class).map(UUIDProperty::getUniqueId).orElse(null);
    }

    private static Integer damage(ItemInstance instance) {
        return instance.getComponent(DurabilityComponent.class).map(DurabilityComponent::getDamage).orElse(null);
    }
}
