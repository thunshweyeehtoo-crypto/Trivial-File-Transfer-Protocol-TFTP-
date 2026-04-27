package com.tftp.tcpserver;

// TCP Server that handles each client request in a separate thread
import java.io.*;
import java.net.*;

public class TCPServer {

    public static final int PORT = 2324;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("TFTP TCP Server running on port " + PORT);

            while (true) {
                // Wait for a client to connect
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Spawn a new thread for each client so multiple transfers can happen at once
                TCPClientHandler handler = new TCPClientHandler(clientSocket);
                handler.start();
            }

        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}