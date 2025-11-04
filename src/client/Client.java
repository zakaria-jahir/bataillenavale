package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Client console minimal pour se connecter au serveur.
 * Utilisation :
 *  - run Client.main()
 *  - suivre les messages du serveur et envoyer commandes (pseudo, mode, tir x y)
 */
public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connecté au serveur " + SERVER_HOST + ":" + SERVER_PORT);

            // Thread d'écoute des messages serveur
            Thread listener = new Thread(() -> {
                try {
                    String serverMsg;
                    while ((serverMsg = in.readLine()) != null) {
                        System.out.println("[Serveur] " + serverMsg);
                    }
                } catch (IOException e) {
                    System.out.println("Connexion fermée.");
                }
            });
            listener.setDaemon(true);
            listener.start();

            // Boucle d'envoi : envoie ce que tape l'utilisateur (pseudo, choix mode, commandes)
            while (true) {
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine();
                out.println(line);
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) break;
            }

            System.out.println("Client terminé.");
        } catch (IOException e) {
            System.err.println("Impossible de se connecter au serveur : " + e.getMessage());
        }
    }
}
