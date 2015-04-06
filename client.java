/*
    Client for reliable file transfer over UDP
    Dan Hoover, Carson Schaefer, Mike Swierenga
    CIS 457: proj 3
*/

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.net.*;
import java.lang.Integer;
import java.util.EnumSet;

class client {

    public static void main(String args[]) {
        
        if (args.length != 3) {
            System.err.println("usage:\n" +
                               "\t1st arg : IP address of server\n" +
                               "\t2nd arg : port of server\n" + 
                               "\t3rd arg : name of file to obtain");
            System.exit(0);
        }
        
        DatagramSocket clientSocket = null;
        InetAddress serverAddr = null;
        SeekableByteChannel fileChannel = null;
        int fileSize = 0;
        int totalPackets = 0;
        
        try {
        
            /* Get IP addr of the server */

            try {
                serverAddr = InetAddress.getByName(args[0]);
                            
            } catch (ArrayIndexOutOfBoundsException x) {
                System.out.println("first argument: invalid IP address");
                System.exit(0);
            } 
                
            /* Get port of the server */
            
            int port = 0;
            
            try {
                port = (Integer.parseInt(args[1]));
                
            } catch (ArrayIndexOutOfBoundsException x) {
                System.err.println("second argument: invalid port number");
                System.exit(0);
                
            } catch (NumberFormatException x) {
                System.err.println("second argument: invalid port number");
                System.exit(0);
            }
            
            if (port > 65535 || port < 1024) {
                System.err.println("second argument: port must be from 1024 to 65535");
            }
            
            /* Open a socket */
             
            clientSocket = new DatagramSocket();
            clientSocket.setSoTimeout(Constants.NO_RESPONSE_TIMEOUT);
        
            /* send request to the server */
            
            byte[] filenameData = args[2].getBytes();
            
            DatagramPacket sendPacket = 
                new DatagramPacket(filenameData, filenameData.length, serverAddr, port);
            clientSocket.send(sendPacket);
            
            /* Receive confirmation or denial */
            
            byte[] recvData = new byte[Constants.PACK_SIZE];
            
            DatagramPacket recvPacket = 
                new DatagramPacket(recvData, Constants.PACK_SIZE);
            clientSocket.receive(recvPacket);
            
            String statusStr = new String(recvPacket.getData());
            
            /* Exit if the file is unavailable */
            
            if (statusStr.startsWith("unable", 0)) {
                System.out.println(statusStr);
                clientSocket.close();
                System.exit(1);
            }
            
            /* Get file size and number of expected packets */
            
            String[] status = statusStr.split(":");

            try {
                fileSize = Integer.parseInt(status[1]);
                totalPackets = Integer.parseInt(status[2]);

            } catch (NumberFormatException e) {
                System.err.println(e);
            }
            
            System.out.println(status[0]);
            System.out.println("file size: " + status[1] + " bytes");
            System.out.println("expected packets: " + status[2]);
            System.out.print("packet traffic: [ ");
                
            // Create a path for output file
            
            Path path = Paths.get(args[2] + ".out");
            
            // Create a SeekableByteChannel object for the path
                        
            fileChannel = Files.newByteChannel(path,
                EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE));
            
            // It is possible that we will receive a duplicate of
            // some of the packets if the server did not receive the
            // acknowledgment or assumed that the packet was lost when
            // it really just took longer than Constants.ACK_TIMEOUT.
            // In this case, we should simply discard the second packet
            // and resend the acknowledgment.
            
            int seqNumber = 0;
                
            boolean[] packetsRcvd = new boolean[totalPackets];
            boolean complete = false;

            while (!complete) {
                // receive packet of data
                
                clientSocket.receive(recvPacket);
                
                // write the packet contents to the appropriate spot in SeekableByteChannel
                
                seqNumber = writeToChannel(recvPacket.getData(), fileChannel);
                
                if (!packetsRcvd[seqNumber]) {
                    packetsRcvd[seqNumber] = true;
                }

                System.out.print("r:" + seqNumber + ", ");
                
                // send acknowledgment number to server
                
                byte[] ack = ByteBuffer.allocate(Constants.HEAD_SIZE).putInt(seqNumber).array();
                
                DatagramPacket sendAck = 
                    new DatagramPacket(ack, ack.length, serverAddr, port);
                clientSocket.send(sendAck);
                
                System.out.print("a:" + seqNumber + ", ");
                
                int count = 0;
                for (boolean a : packetsRcvd) {
                    if (a == false) {
                        break;
                    } else {
                        count++;
                    }
                }        
                if (count == totalPackets) {
                    complete = true;
                }

            }
            
            System.out.println("received and acknowledged all packets ]");
            System.out.println("New file is '" + args[2] + ".out'");
            
        } catch (UnknownHostException e) {
            System.out.println("first argument: invalid IP address");
            System.exit(1);
        } catch (SocketTimeoutException e ) {
            System.err.println("No response from server.");
        } catch (SocketException e) {
            System.err.println("Socket error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
        }
                
        // truncate the file to the correct size
                
        try {
            if (fileChannel != null) {
                fileChannel.truncate((long)fileSize);
            }
        } catch (IOException e) {
            System.err.println("Unable to truncate file: " + e.getMessage());
        }
                
                
        clientSocket.close();
        
    }
    
    /*
        Write packet data to channel and return the sequence number
    */
    public static int
    writeToChannel(byte[] packet, SeekableByteChannel sbc)
    {
        ByteBuffer data = ByteBuffer.allocate(Constants.DATA_SIZE).
                              put(packet, Constants.HEAD_SIZE, Constants.DATA_SIZE);
        data.flip();
        
        ByteBuffer head = ByteBuffer.allocate(4).put(packet, 0, 4);
        head.flip();
        int sequence = head.getInt();
        
        try {
            System.err.println("seq to write to: " + sequence);
            sbc.position(sequence*Constants.DATA_SIZE);
            sbc.write(data);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return sequence;
    }
    
}

