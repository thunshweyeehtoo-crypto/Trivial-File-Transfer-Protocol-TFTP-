package com.tftp.udpserver;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TFTPServer {
    public static final int SERVER_PORT = 2323;

    public static void main(String[] args) {
        try {
            // Bind the server to the specified port and start listening
            DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT);
            System.out.println("UDP Server running on Port:" + SERVER_PORT);

            while (true) {
                // Wait for an incoming packet
                byte[] buffer = new byte[516]; // max TFTP packet size
                DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(incoming);

                // Extract client details
                InetAddress clientAddress = incoming.getAddress();
                int clientPort = incoming.getPort();

                // Copy only the actual received bytes, not the full buffer
                byte[] requestData = new byte[incoming.getLength()];
                System.arraycopy(incoming.getData(), 0, requestData, 0, incoming.getLength());

                int opcode = TFTPPacket.extractOpcode(requestData);
                // Spawn a new thread for each valid RRQ or WRQ request
                if (opcode == TFTPPacket.RRQ || opcode == TFTPPacket.WRQ) {
                    Thread t = new Thread(new TFTPHandling(requestData, clientAddress, clientPort));
                    t.start();
                } else {
                    System.out.println("Ignored unknown opcode: " + opcode);
                }
            }
        } catch (Exception e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}