package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {

    private static final String SERVER_HOST = "172.20.10.3"; // IP du serveur
    private static final int SERVER_PORT = 1234;

    public static void main(String[] args) {

        System.out.println("Connexion au serveur...");

        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("Connecté !");

            // Thread écoute
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("[Serveur] " + line);
                    }
                } catch (Exception ignored) {}
            }).start();

            // Entrée joueur
            while (true) {
                String msg = sc.nextLine();
                out.println(msg);
            }

        } catch (Exception e) {
            System.out.println("Erreur : " + e.getMessage());
        }
    }
}
