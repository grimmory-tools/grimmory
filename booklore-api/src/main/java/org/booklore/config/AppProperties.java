package org.booklore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {
    private String pathConfig;
    private String bookdropFolder;
    private String version;
    private RemoteAuth remoteAuth;
    private Swagger swagger = new Swagger();
    private Boolean forceDisableOidc = false;
    private Telemetry telemetry = new Telemetry();

    /**
     * Type of disk storage where library files are stored.
     * Defaults to LOCAL. Set to NETWORK if using NFS, SMB/CIFS, or other network-mounted storage.
     * Some features like file move/reorganization are disabled on network storage due to
     * unreliable atomic operations that can cause data corruption or loss.
     */
    private String diskType = "LOCAL";

    @Getter
    @Setter
    public static class Swagger {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class RemoteAuth {
        private boolean enabled;
        private boolean createNewUsers;
        private String headerName;
        private String headerUser;
        private String headerEmail;
        private String headerGroups;
        private String adminGroup;
        private String groupsDelimiter = "\\s+";  // Default to whitespace for backward compatibility
    }

    @Getter
    @Setter
    public static class Telemetry {
        private String baseUrl = "https://telemetry.booklore.org";
    }
}
