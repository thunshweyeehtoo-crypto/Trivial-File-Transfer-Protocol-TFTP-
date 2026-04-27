package com.tftp.tcpserver;

//handles a single client connection over TCP
import java.io.*;
import java.net.Socket;

public class TCPClientHandler extends Thread {

    private final Socket client;

    public TCPClientHandler(Socket socket) {
        this.client = socket;
    }

    @Override
    public void run() {
        try (
                DataInputStream input = new DataInputStream(client.getInputStream());
                DataOutputStream output = new DataOutputStream(client.getOutputStream())
        ) {
            // Read the command and filename sent by the client
            String command = input.readUTF();
            String filename = input.readUTF();
            System.out.println("Received command: " + command + " for file: " + filename);

            if ("DOWNLOAD".equalsIgnoreCase(command)) {
                handleDownload(filename, output);
            } else if ("UPLOAD".equalsIgnoreCase(command)) {
                handleUpload(filename, input);
            } else {
                output.writeUTF("ERROR: unrecognised command");
            }

        } catch (IOException e) {
            System.out.println("Client handler error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleDownload(String filename, DataOutputStream output) throws IOException {
        File file = new File(filename);

        // If file doesn't exist, notify the client and stop
        if (!file.exists()) {
            output.writeUTF("ERROR: file not found");
            return;
        }

        output.writeUTF("OK"); // tell client the file was found

        // Send file data in 512 byte chunks
        try (FileInputStream fileIn = new FileInputStream(file)) {
            byte[] buffer = new byte[512];
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                output.writeInt(bytesRead); // tell client how many bytes are coming
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
            output.writeInt(0); // signal end of file
            output.flush();
        }
        System.out.println("Download complete for: " + filename);
    }

    private void handleUpload(String filename, DataInputStream input) throws IOException {
        // Receive file data in chunks and write to disk
        try (FileOutputStream fileOut = new FileOutputStream(filename)) {
            byte[] buffer = new byte[512];
            int bytesRead;

            while ((bytesRead = input.readInt()) > 0) {
                input.readFully(buffer, 0, bytesRead);
                fileOut.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("Upload complete for: " + filename);
    }
}