package com.tftp.udpserver;

//Process individual client requests
import java.io.*;
import java.net.*;

public class TFTPHandling implements Runnable {

    private final byte[] requestData;
    private final InetAddress clientAddress;
    private final int clientPort;

    //store initial request data and clients IP and port
    public TFTPHandling(byte[] requestData, InetAddress clientAddress, int clientPort) {
        this.requestData = requestData;
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            // Each client gets its own socket on a random port
            socket = new DatagramSocket();
            socket.setSoTimeout(5000); // 5 second timeout before retransmit

            int opcode = TFTPPacket.extractOpcode(requestData);
            if (opcode == TFTPPacket.RRQ) {
                handleReadRequest(socket);
            } else if (opcode == TFTPPacket.WRQ) {
                handleWriteRequest(socket);
            } else {
                System.out.println("Unsupported.");
            }
        } catch (Exception e) {
            System.out.println("Error in client handler: " + e.getMessage());
        } finally {
            // Always close the socket when done
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void handleReadRequest(DatagramSocket socket) throws IOException {
        String filename = TFTPPacket.extractFilename(requestData);
        System.out.println("Client requested the file " + filename);

        File file = new File(filename);
        // If the file doesn't exist, send error code 1 and stop
        if (!file.exists()) {
            byte[] errorPacket = TFTPPacket.buildERROR(1, "File not found");
            socket.send(new DatagramPacket(errorPacket, errorPacket.length, clientAddress, clientPort));
            System.out.println("File not found: " + filename);
            return;
        }

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[TFTPPacket.BLOCK_SIZE];
        int blockNum = 1;
        boolean lastBlock = false;

        while (!lastBlock) {
            int bytesRead = fis.read(buffer);

            if (bytesRead < 0) {
                // End of file reached so nothing to send
                bytesRead = 0;
                lastBlock = true;
            } else if (bytesRead < TFTPPacket.BLOCK_SIZE) {
                // Any block smaller than 512 bytes is the final block
                lastBlock = true;
            }

            // Build and send the DATA packet to the client
            byte[] dataPacket = TFTPPacket.buildDATA(blockNum, buffer, bytesRead);
            DatagramPacket outgoing = new DatagramPacket(dataPacket, dataPacket.length, clientAddress, clientPort);
            socket.send(outgoing);

            boolean ackReceived = false;
            while (!ackReceived) {
                try {
                    byte[] ackBuf = new byte[4];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                    socket.receive(ackPacket);

                    int ackOpcode = TFTPPacket.extractOpcode(ackBuf);
                    int ackBlock = TFTPPacket.extractBlockNumber(ackBuf);

                    // Only move on if we got the correct ACK for the current block
                    if (ackOpcode == TFTPPacket.ACK && ackBlock == blockNum) {
                        ackReceived = true;
                    }
                } catch (SocketTimeoutException e) {
                    // resend the same block
                    System.out.println("Timeout on block " + blockNum + ", retransmitting...");
                    socket.send(outgoing);
                }
            }
            blockNum++;
        }
        fis.close();
        System.out.println("Transfer complete for: " + filename);
    }

    private void handleWriteRequest(DatagramSocket socket) throws IOException {
        String filename = TFTPPacket.extractFilename(requestData);
        System.out.println("Upload requested from client for: " + filename);

        // Send ACK 0 to signal the client to begin sending data
        byte[] ack0 = TFTPPacket.buildACK(0);
        socket.send(new DatagramPacket(ack0, ack0.length, clientAddress, clientPort));

        int expectedBlock = 1;
        boolean transferDone = false;
        FileOutputStream fos = null;

        try {
            while (!transferDone) {
                try {
                    byte[] recvBuf = new byte[516];
                    DatagramPacket incoming = new DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(incoming);

                    int opcode = TFTPPacket.extractOpcode(recvBuf);
                    int blockNum = TFTPPacket.extractBlockNumber(recvBuf);

                    if (opcode == TFTPPacket.DATA && blockNum == expectedBlock) {
                        byte[] fileData = TFTPPacket.extractData(recvBuf, incoming.getLength());

                        // Only create the file once we actually receive data
                        if (fos == null) {
                            fos = new FileOutputStream(filename);
                        }
                        fos.write(fileData);

                        byte[] ack = TFTPPacket.buildACK(blockNum);
                        socket.send(new DatagramPacket(ack, ack.length, clientAddress, clientPort));

                        if (fileData.length < TFTPPacket.BLOCK_SIZE) {
                            transferDone = true;
                        }
                        expectedBlock++;
                    } else if (opcode == TFTPPacket.DATA && blockNum != expectedBlock) {
                        // Duplicate or out of order block
                        byte[] ack = TFTPPacket.buildACK(expectedBlock - 1);
                        socket.send(new DatagramPacket(ack, ack.length, clientAddress, clientPort));
                    } else {
                        System.out.println("Unexpected packet, opcode: " + opcode);
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Upload timed out for:" + expectedBlock);
                    break;
                }
            }
            System.out.println("Upload completed for: " + filename);
        } finally {
            if (fos != null) fos.close();
        }
    }
}