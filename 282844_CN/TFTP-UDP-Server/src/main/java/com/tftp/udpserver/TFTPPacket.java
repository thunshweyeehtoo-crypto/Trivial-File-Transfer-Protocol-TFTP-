package com.tftp.udpserver;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TFTPPacket {

    // Opcodes defined by RFC 1350
    public static final short RRQ   = 1;
    public static final short WRQ   = 2;
    public static final short DATA  = 3;
    public static final short ACK   = 4;
    public static final short ERROR = 5;

    public static final int BLOCK_SIZE = 512; //max size

    // Build a read request packet
    public static byte[] buildRRQ(String filename) {

        return buildRequestPacket(RRQ, filename);
    }
    // Build a write request packet
    public static byte[] buildWRQ(String filename) {

        return buildRequestPacket(WRQ, filename);
    }

    private static byte[] buildRequestPacket(short opcode, String filename) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(0); //first byte must be 0
            out.write(opcode); //1 or 2
            out.write(filename.getBytes(StandardCharsets.UTF_8));
            out.write(0); //null terminator
            out.write("octet".getBytes(StandardCharsets.UTF_8)); //always octet
            out.write(0);
        }
        catch (IOException e) {
            System.out.println("Failed to build request packet: " + e.getMessage());
        }
        return out.toByteArray();
    }

    // Build a DATA packet
    public static byte[] buildDATA(int blockNum, byte[] data, int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(DATA);
        out.write((blockNum >> 8) & 0xFF);
        out.write(blockNum & 0xFF);
        out.write(data, 0, length);
        return out.toByteArray();
    }

    // Build an ACK packet
    public static byte[] buildACK(int blockNum) {
        return new byte[]{
                0, ACK,
                (byte) ((blockNum >> 8) & 0xFF),
                (byte) (blockNum & 0xFF)
        };
    }

    // Build an ERROR packet
    public static byte[] buildERROR(int errorCode, String message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(ERROR);
        out.write((errorCode >> 8) & 0xFF); //high byte
        out.write(errorCode & 0xFF); //low byte
        try {
            out.write(message.getBytes(StandardCharsets.UTF_8));
            out.write(0);
        } catch (IOException e) {
            System.out.println("Failed to build error packet: " + e.getMessage());
        }
        return out.toByteArray();
    }
    public static String extractErrorMessage(byte[] packet, int length) {
        int end = 4;
        while (end < length && packet[end] != 0) {
            end++;
        }
        return new String(packet, 4, end - 4);
    }

    // Extract opcode from any packet (always byte index 1)
    public static int extractOpcode(byte[] packet) {

        return packet[1];
    }

    // Extract block number from DATA or ACK packets (bytes 2 and 3)
    public static int extractBlockNumber(byte[] packet) {

        return ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
    }

    // Extract filename from RRQ or WRQ packets
    public static String extractFilename(byte[] packet) {
        int start = 2;
        int end = start;
        while (end < packet.length && packet[end] != 0) {
            end++;
        }
        return new String(packet, start, end - start, StandardCharsets.UTF_8);
    }

    // get the opcode from a packet
    public static byte[] extractData(byte[] packet, int packetLength) {
        return Arrays.copyOfRange(packet, 4, packetLength);
    }
}