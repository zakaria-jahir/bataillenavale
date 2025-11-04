package clientfx;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Petite abstraction réseau : socket TCP, thread d'écoute et envoi simple.
 * Le callback (Consumer<String>) est appelé à chaque ligne reçue du serveur.
 */
public class NetworkClient {
    private final String host;
    private final int port;
    private final Consumer<String> onMessage;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;

    public NetworkClient(String host, int port, Consumer<String> onMessage) {
        this.host = host;
        this.port = port;
        this.onMessage = onMessage;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void startListening() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    onMessage.accept(line);
                }
            } catch (IOException e) {
                onMessage.accept("[Réseau] Connexion interrompue : " + e.getMessage());
            } finally {
                close();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void send(String msg) {
        if (out != null) out.println(msg);
    }

    public synchronized void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }
}
