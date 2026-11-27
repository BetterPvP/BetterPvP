package me.mykindos.betterpvp.core.framework.role;

import lombok.CustomLog;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether a feature installs on this server, from the {@link RunsOn} nearest to its class.
 */
@CustomLog
public final class RoleGate {

    @Getter
    private final ServerRole role;
    private final Map<String, Optional<RunsOn>> packageDeclarations = new ConcurrentHashMap<>();

    public RoleGate(ServerRole role) {
        this.role = role;
    }

    /**
     * The gate for the role this process was started with.
     */
    public static RoleGate current() {
        return Holder.CURRENT;
    }

    public boolean allows(Class<?> clazz) {
        if (role == ServerRole.ALL) {
            return true;
        }

        return declarationFor(clazz).map(this::includesRole).orElse(true);
    }

    private boolean includesRole(RunsOn runsOn) {
        for (ServerRole declared : runsOn.value()) {
            if (declared == role || declared == ServerRole.ALL) {
                return true;
            }
        }
        return false;
    }

    private Optional<RunsOn> declarationFor(Class<?> clazz) {
        for (Class<?> type = clazz; type != null; type = type.getEnclosingClass()) {
            final RunsOn runsOn = type.getAnnotation(RunsOn.class);
            if (runsOn != null) {
                return Optional.of(runsOn);
            }
        }

        String packageName = clazz.getPackageName();
        while (!packageName.isEmpty()) {
            final Optional<RunsOn> declared = packageDeclaration(packageName, clazz.getClassLoader());
            if (declared.isPresent()) {
                return declared;
            }
            final int dot = packageName.lastIndexOf('.');
            packageName = dot < 0 ? "" : packageName.substring(0, dot);
        }
        return Optional.empty();
    }

    private Optional<RunsOn> packageDeclaration(String packageName, ClassLoader loader) {
        return packageDeclarations.computeIfAbsent(packageName, name -> {
            try {
                final Class<?> packageInfo = Class.forName(name + ".package-info", false, loader);
                return Optional.ofNullable(packageInfo.getAnnotation(RunsOn.class));
            } catch (ClassNotFoundException e) {
                return Optional.empty();
            }
        });
    }

    private static final class Holder {
        private static final RoleGate CURRENT = create();

        private static RoleGate create() {
            final ServerRole role = ServerRole.parse(System.getenv(ServerRole.ENVIRONMENT_VARIABLE));
            log.info("Server role is {}", role.getId()).submit();
            return new RoleGate(role);
        }
    }

}
