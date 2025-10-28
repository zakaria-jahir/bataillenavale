package udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpAuthServer {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(4445);
        byte[] buffer = new byte[256];
        System.out.println("Serveur UDP démarré...");

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Reçu: " + message);

            String response = message.equals("login") ? "ok" : "invalid";
            byte[] responseData = response.getBytes();

            DatagramPacket responsePacket =
                    new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort());
            socket.send(responsePacket);
        }
    }
}
