package me.mykindos.betterpvp.core.playerstate;

import com.google.inject.Guice;
import com.google.inject.Provider;
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
import me.mykindos.betterpvp.core.item.component.serialization.ComponentSerializationRegistry;
import me.mykindos.betterpvp.core.item.component.serialization.impl.UUIDPropertySerializer;
import me.mykindos.betterpvp.core.item.component.impl.uuid.UUIDProperty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class PlayerStateStoreTest {

    private static final NamespacedKey BLADE = new NamespacedKey("test", "blade");

    private static PostgreSQLContainer<?> postgres;
    private static DataSource dataSource;

    private ServerMock server;
    private ItemFactory itemFactory;
    private ItemDataCodec itemCodec;
    private DatabasePlayerStateStore store;
    private PlayerStateCapture capture;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        final PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        dataSource = source;
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:core-migrations/postgres/playerstate")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopDatabase() {
        postgres.stop();
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
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

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void saveThenLoadKeepsEveryCustomItemIdentical() {
        final PlayerMock player = server.addPlayer();
        final ItemStack sword = blade(17);
        assertNotNull(uuid(itemFactory.fromItemStack(sword).orElseThrow()));
        final ItemStack helmet = blade(3);
        final ItemStack offhand = blade(0);
        final ItemStack stored = blade(40);
        final ItemStack dirt = new ItemStack(Material.DIRT, 23);
        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(5, dirt);
        player.getInventory().setItem(39, helmet);
        player.getInventory().setItem(40, offhand);
        player.getEnderChest().setItem(12, stored);
        player.setFoodLevel(7);
        player.setLevel(31);
        player.setGameMode(GameMode.ADVENTURE);

        assertTrue(store.save(capture.capture(player, 1)).join());

        final PlayerMock other = server.addPlayer();
        final PlayerState loaded = store.load(player.getUniqueId()).join().orElseThrow();
        capture.apply(other, loaded);

        assertSameItem(sword, other.getInventory().getItem(0));
        assertSameItem(dirt, other.getInventory().getItem(5));
        assertSameItem(helmet, other.getInventory().getItem(39));
        assertSameItem(offhand, other.getInventory().getItem(40));
        assertSameItem(stored, other.getEnderChest().getItem(12));
        assertEquals(1, loaded.getVersion());
        assertEquals(7, other.getFoodLevel());
        assertEquals(31, other.getLevel());
        assertEquals(GameMode.ADVENTURE, other.getGameMode());
    }

    @Test
    void staleSaveIsRejected() {
        final PlayerMock player = server.addPlayer();
        player.setLevel(1);
        assertTrue(store.save(capture.capture(player, 1)).join());

        player.setLevel(2);
        assertTrue(store.save(capture.capture(player, 2)).join());

        player.setLevel(99);
        assertFalse(store.save(capture.capture(player, 1)).join());
        assertFalse(store.save(capture.capture(player, 2)).join());

        final PlayerState loaded = store.load(player.getUniqueId()).join().orElseThrow();
        assertEquals(2, loaded.getVersion());
        assertEquals(2, loaded.getLevel());
    }

    @Test
    void unsavedPlayerLoadsNothing() {
        assertTrue(store.load(UUID.randomUUID()).join().isEmpty());
    }

    private ItemStack blade(int damage) {
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

    // MockBukkit compares nested byte arrays by reference, so the persistent data is compared through its encoding
    private void assertSameItem(ItemStack expected, ItemStack actual) {
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getAmount(), actual.getAmount());
        assertEquals(itemCodec.encode(expected), itemCodec.encode(actual));
        final ItemInstance before = itemFactory.fromItemStack(expected).orElseThrow();
        final ItemInstance after = itemFactory.fromItemStack(actual).orElseThrow();
        assertEquals(itemFactory.getItemRegistry().getKey(before.getBaseItem()), itemFactory.getItemRegistry().getKey(after.getBaseItem()));
        assertEquals(uuid(before), uuid(after));
        assertEquals(damage(before), damage(after));
    }

    private static UUID uuid(ItemInstance instance) {
        return instance.getComponent(UUIDProperty.class).map(UUIDProperty::getUniqueId).orElse(null);
    }

    private static Integer damage(ItemInstance instance) {
        return instance.getComponent(DurabilityComponent.class).map(DurabilityComponent::getDamage).orElse(null);
    }
}
