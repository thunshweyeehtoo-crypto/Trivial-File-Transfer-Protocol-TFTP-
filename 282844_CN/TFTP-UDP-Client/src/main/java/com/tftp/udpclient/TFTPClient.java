package com.tftp.udpclient;

// Sends a Read Request to a server and downloads a file using TFTP protocol over UDP
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TFTPClient {

    private static final int SERVER_PORT = 2323;
    private static final int TIMEOUT = 5000;
    private static final int MAX_PACKET_SIZE = 516;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        //Asking user for server IP address
        System.out.print("Enter server IP: ");
        String serverIP = scanner.nextLine().trim();

        // Ask user whether they want to download or upload
        System.out.println("Select an option:");
        System.out.println("1- Download");
        System.out.println("2- Upload");
        System.out.print("Your choice: ");
        int choice = scanner.nextInt();
        scanner.nextLine();

        System.out.print("Enter file name: ");
        String filename = scanner.nextLine().trim();

        try (DatagramSocket socket = new DatagramSocket()) {
            //create UDP socket
            socket.setSoTimeout(TIMEOUT);
            InetAddress serverAddress = InetAddress.getByName(serverIP);

            if (choice == 1) {
                byte[] rrqPacket = TFTPPacket.buildRRQ(filename);
                socket.send(new DatagramPacket(rrqPacket, rrqPacket.length, serverAddress, SERVER_PORT));
                System.out.println("Download request sent for: " + filename);

                FileOutputStream fileOut = null;   // don't open yet
                int expectedBlock = 1;
                boolean finished = false;
                boolean errorReceived = false;

                while (!finished) {
                    byte[] buffer = new byte[MAX_PACKET_SIZE];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);

                    try {
                        socket.receive(response);
                    } catch (SocketTimeoutException e) {
                        System.out.println("No response from server, transfer aborted.");
                        if (fileOut != null) fileOut.close();
                        return;
                    }

                    byte[] responseData = response.getData();
                    int opcode = TFTPPacket.extractOpcode(responseData);

                    if (opcode == TFTPPacket.DATA) {
                        int blockNum = TFTPPacket.extractBlockNumber(responseData);

                        if (blockNum == expectedBlock) {
                            // open the file only now that we know we're really getting data
                            if (fileOut == null) {
                                fileOut = new FileOutputStream(filename);
                            }

                            byte[] fileChunk = TFTPPacket.extractData(responseData, response.getLength());
                            fileOut.write(fileChunk);

                            byte[] ack = TFTPPacket.buildACK(blockNum);
                            socket.send(new DatagramPacket(ack, ack.length, response.getAddress(), response.getPort()));

                            if (fileChunk.length < TFTPPacket.BLOCK_SIZE) {
                                finished = true;
                            }
                            expectedBlock++;
                        }
                    } else if (opcode == TFTPPacket.ERROR) {
                        String errMsg = TFTPPacket.extractErrorMessage(responseData, response.getLength());
                        System.out.println("Server error: " + errMsg);
                        errorReceived = true;
                        break;
                    }
                }

                if (fileOut != null) fileOut.close();
                if (!errorReceived) {
                    System.out.println("Download complete: " + filename);
                }

            } else if (choice == 2) {
                File file = new File(filename);
                // Make sure the file exists before attempting upload
                if (!file.exists()) {
                    System.out.println("File not found locally: " + filename);
                    return;
                }

                // Send WRQ to initiate the upload
                byte[] wrqPacket = TFTPPacket.buildWRQ(filename);
                socket.send(new DatagramPacket(wrqPacket, wrqPacket.length, serverAddress, SERVER_PORT));
                System.out.println("Upload request sent for: " + filename);

                // Wait for ACK 0 from server before sending any data
                byte[] ackBuffer = new byte[4];
                DatagramPacket ack0 = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.receive(ack0);

                // Use the server's TID from ACK 0 for the rest of the transfer
                InetAddress serverDataAddress = ack0.getAddress();
                int serverDataPort = ack0.getPort();

                FileInputStream fileIn = new FileInputStream(file);
                byte[] chunk = new byte[TFTPPacket.BLOCK_SIZE];
                int blockNum = 1;
                boolean finished = false;

                while (!finished) {
                    int bytesRead = fileIn.read(chunk);

                    if (bytesRead == -1) {
                        // File was exact multiple of 512 — send empty final block to signal end
                        byte[] emptyBlock = TFTPPacket.buildDATA(blockNum, new byte[0], 0);
                        socket.send(new DatagramPacket(emptyBlock, emptyBlock.length, serverDataAddress, serverDataPort));
                        break;
                    }
                    if (bytesRead < TFTPPacket.BLOCK_SIZE) {
                        finished = true; // this is the last block
                    }

                    // Build and send the DATA packet
                    byte[] dataPacket = TFTPPacket.buildDATA(blockNum, chunk, bytesRead);
                    DatagramPacket sendPacket = new DatagramPacket(dataPacket, dataPacket.length, serverDataAddress, serverDataPort);
                    socket.send(sendPacket);

                    boolean gotAck = false;
                    while (!gotAck) {
                        try {
                            byte[] ackCheck = new byte[4];
                            DatagramPacket ackReply = new DatagramPacket(ackCheck, ackCheck.length);
                            socket.receive(ackReply);

                            int ackOpcode = TFTPPacket.extractOpcode(ackCheck);
                            int ackBlock = TFTPPacket.extractBlockNumber(ackCheck);

                            if (ackOpcode == TFTPPacket.ACK && ackBlock == blockNum) {
                                gotAck = true;
                            }
                        } catch (SocketTimeoutException e) {
                            // No ACK received in time so retransmit the block
                            System.out.println("Timeout on block " + blockNum + ", retransmitting...");
                            socket.send(sendPacket);
                        }
                    }
                    blockNum++;
                }
                fileIn.close();
                System.out.println("Upload complete: " + filename);

            } else {
                System.out.println("Invalid choice.");
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}