package com.tftp.tcpclient;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TCPClient {

    private static final int SERVER_PORT = 2324;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Ask user for server IP
        System.out.print("Enter server IP: ");
        String serverIP = scanner.nextLine().trim();

        // Ask user whether they want to download or upload
        System.out.println("Choose an option:");
        System.out.println("1- Download");
        System.out.println("2- Upload");
        System.out.print("Your choice: ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume leftover newline from nextInt

        System.out.print("Enter file name: ");
        String filename = scanner.nextLine().trim();

        try (
                Socket socket = new Socket(serverIP, SERVER_PORT); // connect to server
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            if (choice == 1) {
                // Tell the server we want to download a file
                out.writeUTF("DOWNLOAD");
                out.writeUTF(filename);

                // Check server response before receiving data
                String response = in.readUTF();
                if (response.startsWith("ERROR")) {
                    System.out.println("Server error: " + response);
                    return;
                }

                System.out.println("File found on server, downloading...");

                // Receive file chunks and write to disk
                FileOutputStream fileOut = new FileOutputStream(filename);
                byte[] buffer = new byte[512];
                int chunkSize;

                while ((chunkSize = in.readInt()) > 0) {
                    in.readFully(buffer, 0, chunkSize); // read exactly chunkSize bytes
                    fileOut.write(buffer, 0, chunkSize);
                }
                fileOut.close();
                System.out.println("Download complete: " + filename);

            } else if (choice == 2) {
                File file = new File(filename);
                // Make sure file exists locally before attempting upload
                if (!file.exists()) {
                    System.out.println("File not found locally: " + filename);
                    return;
                }

                // Tell the server we want to upload a file
                out.writeUTF("UPLOAD");
                out.writeUTF(filename);

                // Send file in 512 byte chunks
                FileInputStream fileIn = new FileInputStream(file);
                byte[] buffer = new byte[512];
                int bytesRead;

                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    out.writeInt(bytesRead); // tell server how many bytes are in this chunk
                    out.write(buffer, 0, bytesRead);
                }
                out.writeInt(0); // signal end of file to server
                out.flush();
                fileIn.close();

                System.out.println("Upload complete: " + filename);

            } else {
                System.out.println("Invalid choice.");
            }

        } catch (IOException e) {
            System.out.println("Something went wrong: " + e.getMessage());
        }
    }
}