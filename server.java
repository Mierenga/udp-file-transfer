/*
    Server for reliable file transfer over UDP
    Dan Hoover, Carson Schaefer, Mike Swierenga
    CIS 457: proj 3
*/


import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.net.*;
import java.lang.Integer;
import java.lang.Math.*;

class server {
    
    public static final int DATA_SIZE = 1000;
    public static final int HEAD_SIZE = 4;
    public static final int PACK_SIZE = DATA_SIZE + HEAD_SIZE;
    
    public static void main(String args[]) {

    
        /* Get port number from arg */
        
        int port = 0;
        
        try {
            port = (Integer.parseInt(args[0]));
            
            if (port > 65535 || port < 1024) {
                System.err.println("port must be from 1024 to 65535");
                System.exit(0);
            }
            
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("must enter port number as first argument");
            System.exit(0);
            
        } catch (NumberFormatException e) {
            System.err.println("must enter port number as first argument");
            System.exit(0);
        }
        
        
        DatagramSocket serverSocket = null;
        InetAddress clientAddr = null;
        int clientPort = 0;
        String fileName = null;
        
        try {
            // Open a socket to listen
        
            serverSocket = new DatagramSocket(port);
        
        } catch (SocketException e) {
			    System.err.println(e);
			    System.exit(1);
        }
        
        
        for(;;) {
        
            try {

                // Receive a file request 
                
                byte[] data = new byte[PACK_SIZE];

                DatagramPacket recvPacket = 
                    new DatagramPacket(data,data.length);
                serverSocket.receive(recvPacket);
                
                fileName = new String(recvPacket.getData()).trim();

                // Save client addr and port            
                
                clientAddr = recvPacket.getAddress();
                clientPort = recvPacket.getPort();
	            
	            System.out.println("server received request for '" +
	                fileName + "'" + " from " + clientAddr.toString() + " [" + clientPort + "]" );
	            
                // Create a path from the file name,
                //   can throw NoSuchFileException
                
                
            	Path path = Paths.get(fileName).toRealPath(); 
                        
            	if (!Files.exists(path)) {
            	
            	    serverSocket.send(
            	        assembleDenialPacket(fileName, clientAddr, clientPort));
                            
            	} else {
            	
                	// Create a SeekableByteChannel object for the path
                	
                	SeekableByteChannel fileChannel = Files.newByteChannel(path, StandardOpenOption.READ);
                	
                	// setup the sliding window
                	
                	double dFileSize = fileChannel.size();
                	                	
                	int totalPackets = (int) Math.ceil(dFileSize / DATA_SIZE);
                    int fileSize = (int) dFileSize;
                	
                	serverSocket.send (
            	        assembleConfirmPacket (
                            fileName, fileSize, totalPackets, clientAddr, clientPort));
                	
                	
                	
                	int windowBottom = 0;
                	int sequence = 0;
                	
                    // Construct and send the first five packets

                    for (int i = 0; i < 5; i++) {
                        
                        if (sequence < totalPackets) {
                            
                            serverSocket.send(
                                constructNextPacket(
                                    fileChannel, clientAddr, clientPort, sequence++));
                                    
                            System.out.println("  + sent packet " + i);
                        } else {
                            break;
                        }
                        
                    }
	                    
                    // listen for acknowledgments from client
                    
                    
                    // while (incomplete != 0) {
            			// listen for any ack
            			
            			//serverSocket.receive(recvPacket);
            			
            			// parse ack for sequence number
            			// find that sequence number in window array
            			// increment the count
            			// if (count > maxCount)
            				// set window slot to null
            				// decrement incomplete
            			// else
            				// replace the contents with loadWindow
            				// send the packet
            			// 
            		// }
	                
	            	
	            }
	            	
            } catch (NoSuchFileException x) {
            
                // Send and print denial of request
                try {
                	serverSocket.send(
                	    assembleDenialPacket(fileName, clientAddr, clientPort));
                            
            	} catch (IOException e) {
            	    System.err.println(e);
            	}
            	
            } catch (IOException e) {
			    System.err.println(e);
			    System.exit(1);
		    } catch (IllegalArgumentException e) {
		        System.err.println(e);
			    System.exit(1);
		    }
        }
        
        //serverSocket.close();
    }
    
    /* 
        Returns a DatagramPacket with request confirmation information, ready to be sent
    */
    public static DatagramPacket
    assembleConfirmPacket(String fn, int fs, int tp, InetAddress addr, int port)
    {
        String msg = new String("server received request for '" + fn + "'");
        System.out.println("  file size: " + fs);
        System.out.println("  packets to send: " + tp);
        msg += (":" + fs + ":" + tp + ":");
        	
    	byte[] data = msg.getBytes();
        	
    	return new DatagramPacket(data, data.length, addr, port);
    }
    
    /* 
        Returns a DatagramPacket with request denial information, ready to be sent
    */
    public static DatagramPacket
    assembleDenialPacket(String fn, InetAddress addr, int port)
    {
        String msg = new String("unable to locate '" + fn + "' on server");
        System.out.println("  - " + msg);
    	byte[] data = msg.getBytes();
    	return new DatagramPacket(data, data.length, addr, port);
    }
    
    /*
        Returns a DatagramPacket with 1000 bytes of file data, ready to be sent
    */
    public static DatagramPacket
    constructNextPacket(SeekableByteChannel sbc, InetAddress addr, int port, int seq)
    {
        ByteBuffer buf = ByteBuffer.allocate(DATA_SIZE);
        try {
            sbc.read(buf);
        } catch (IOException e) {
            System.err.println(e);
        }
        byte[] data = new byte[PACK_SIZE];
        data = addHead(seq, buf);
        
        return new DatagramPacket(data, data.length, addr, port);
    }
    /*
        Helper for constructNextPacket()
        Returns a byte array with header and data, ready to be loaded into a packet
    */
    public static byte[]
    addHead(int seq, ByteBuffer data)
    {
        byte[] packArr = new byte[PACK_SIZE];
        byte[] headArr = ByteBuffer.allocate(HEAD_SIZE).putInt(seq).array();
        
        System.arraycopy(headArr, 0, packArr, 0, HEAD_SIZE);
        System.arraycopy(data.array(), 0, packArr, 4, DATA_SIZE);
        
        return packArr;
    }
    
}
