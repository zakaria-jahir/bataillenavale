package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Client Bataille Navale (TCP)
 * - Se connecte automatiquement à un serveur prédéfini (IP + port)
 * - Ne demande plus à l'utilisateur de saisir l'adresse
 */
public class Client {

    // -----------------------------
    // 🧭 CONFIGURATION STATIQUE
    // -----------------------------
    private static final String SERVER_HOST = "172.20.10.3";
    private static final int SERVER_PORT = 1234;

    public static void main(String[] args) {
        System.out.println("🔵 Client Bataille Navale");
        System.out.println("Connexion au serveur " + SERVER_HOST + ":" + SERVER_PORT + " ...");

        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("✅ Connecté au serveur.");

            // Thread pour écouter les messages du serveur
            Thread listener = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("[Serveur] " + line);
                    }
                } catch (IOException e) {
                    System.out.println("❌ Connexion fermée : " + e.getMessage());
                }
            });
            listener.setDaemon(true);
            listener.start();

            // Interaction utilisateur simple (envoyer pseudo et commandes)
            while (true) {
                String input = scanner.nextLine();
                out.println(input);
                if (input.equalsIgnoreCase("quit")) {
                    System.out.println("👋 Déconnexion...");
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Erreur de connexion au serveur : " + e.getMessage());
            System.err.println("Vérifie que le serveur est bien lancé et que l'adresse IP est correcte.");
        }
    }
}
