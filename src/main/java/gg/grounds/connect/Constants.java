package gg.grounds.connect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants for the Grounds Connect mod. */
public final class Constants {
    private Constants() {}

    public static final String MOD_ID = "grounds_connect";
    public static final String MOD_NAME = "Grounds Connect";
    public static final Logger LOG = LoggerFactory.getLogger("grounds-connect");

    /** Keycloak realm issuer (public client, device-code grant — reused from the Grounds CLI). */
    public static final String KEYCLOAK_ISSUER = "https://account.grounds.gg/realms/grounds";
    public static final String KEYCLOAK_CLIENT_ID = "grounds-cli";
    public static final String KEYCLOAK_SCOPE = "openid profile email offline_access";

    /** Default forge platform API base; overridable via config.json or GROUNDS_API_URL. */
    public static final String DEFAULT_API_BASE_URL = "https://platform.grnds.io";

    /** publicUrl scheme that marks a connectable Minecraft server in the deployments API. */
    public static final String MINECRAFT_URL_SCHEME = "minecraft://";
    public static final int DEFAULT_MC_PORT = 25565;

    public static String deviceAuthEndpoint() {
        return KEYCLOAK_ISSUER + "/protocol/openid-connect/auth/device";
    }

    public static String tokenEndpoint() {
        return KEYCLOAK_ISSUER + "/protocol/openid-connect/token";
    }
}
