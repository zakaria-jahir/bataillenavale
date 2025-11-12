package clientawt;

/**
 * Démarre le serveur de bataille navale (classe Server dans bataillenavale.server)
 * dans un thread séparé si ce n'est pas déjà fait.
 *
 * Important : la classe Server doit déjà exister dans le package bataillenavale.server
 * (fichier Server.java fourni précédemment).
 */
public class ServerLauncher {
    private static volatile boolean serverStarted = false;

    public static synchronized void startServerIfNeeded() {
        if (serverStarted) return;
        serverStarted = true;

        Thread serverThread = new Thread(() -> {
            try {
                // appelle la méthode main du serveur
                server.Server.main(new String[]{});
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "BatailleNavale-Server-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        try { Thread.sleep(400); } catch (InterruptedException ignored) {}
    }

    public static boolean isServerStarted() { return serverStarted; }
}
