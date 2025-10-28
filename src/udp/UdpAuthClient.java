package udp;

import java.net.*;

public class UdpAuthClient {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("localhost");

        byte[] data = "login".getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, 4445);
        socket.send(packet);

        byte[] buffer = new byte[256];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);

        String reply = new String(response.getData(), 0, response.getLength());
        System.out.println("Réponse du serveur : " + reply);

        socket.close();
    }
}
