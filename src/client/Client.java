package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {

    private static final String HOST = "172.20.10.3";
    private static final int PORT = 1234;

    public static void main(String[] args) {

        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("Connecté au serveur.");

            // lecture serveur
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("[SERVEUR] " + line);
                    }
                } catch (Exception ignored) {}
            }).start();

            // envoi client
            while (true) {
                out.println(sc.nextLine());
            }

        } catch (Exception e) {
            System.out.println("Erreur : " + e.getMessage());
        }
    }
}
