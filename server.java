/*
    Server for reliable file transfer over UDP
    Dan Hoover, Carson Schaefer, Mike Swierenga
    CIS 457: proj 3
*/


import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.lang.Integer;

class server {
    
    public static final int SIZE = 1024;
    
    public static void main(String args[]) {

    
        /* Get port number from arg */
        int port = 0;
        try {
            port = (Integer.parseInt(args[0]));
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("must enter port number as first argument");
            System.exit(0);
        } catch (NumberFormatException e) {
            System.err.println("must enter port number as first argument");
            System.exit(0);
        }
        if (port > 65535 || port < 1024) {
            System.err.println("port must be from 1024 to 65535");
            System.exit(0);
        }
        
        
        try {
        
            /* Open a socket to listen */
            
			DatagramSocket serverSocket = new DatagramSocket(port);
            
            /* Receive a file request */
            
            byte[] data = new byte[SIZE];

            DatagramPacket recvPacket = 
                new DatagramPacket(data,data.length);
            serverSocket.receive(recvPacket);
            String fileName = new String(recvPacket.getData());
            fileName = fileName.trim();
            
            /* Send a confirmation of request */
            
            String confirmation = new String("server received request for '" + fileName + "'");
            InetAddress clientAddr = recvPacket.getAddress();
            int clientPort = recvPacket.getPort();
            System.out.println( confirmation + " from " + clientAddr.toString() + " [" + clientPort + "]" );
            
            
            /* Open the file if it is available */
            
            ByteBuffer fileData = null;
            
	        try {
	        	Path path = Paths.get( fileName );
	        	fileData = ByteBuffer.wrap( Files.readAllBytes(path));
	        } catch (NoSuchFileException e) {
	        	confirmation = new String("unable to locate '" + fileName + "' on server");
	        	System.out.println(confirmation);
	        }
	        data = confirmation.getBytes();
            DatagramPacket sendPacket =
                new DatagramPacket(data, data.length, clientAddr, clientPort);
            serverSocket.send(sendPacket);
            
	        /* Begin sending the file data */
	        
            if (fileData != null) {
            
		        byte[][] window = new byte[5][1024];
		        int count = 0;
		        int incomplete = 5;
		        // find the total number of packets that will be sent
		        final int maxCount = (int) (fileData.limit() / 1020);
	        
	        	// load first 5 windows
	    		for (int i = 0; i < 5 ; i++ ) {
	    			window[i] = loadWindow(count, fileData);
	    		}
	        	
	    		// send first 5 packets
	    		//
	    		//
	    		
	    		// while (incomplete != 0) {
	    			// listen for any ack
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
	    		
	        	for (byte[] arr : window) {
	        		System.out.println(arr);
	        		
	        	}
	        	
	        }

            serverSocket.close();
            
        } catch (SocketException e) {
			System.err.println(e);
			System.exit(1);
		} catch (IOException e) {
			System.err.println(e);
			System.exit(1);
		} catch (IllegalArgumentException e) {
		    System.err.println(e);
			System.exit(1);
		}
        
        
    }
    
    public static byte[] concat(byte[] a, byte[] b) {
    	   int aLen = a.length;
    	   int bLen = b.length;
    	   byte[] c = new byte[aLen+bLen];
    	   System.arraycopy(a, 0, c, 0, aLen);
    	   System.arraycopy(b, 0, c, aLen, bLen);
    	   return c;
   }
    public static byte[] IntToByteArray(int i){
	    return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(i).array();
   }
    
   public static byte[] loadWindow(int sequence, ByteBuffer bb) {
	   byte[] data = new byte[1020];
	   try {
		   bb.get(data);
	   } catch (BufferUnderflowException e) {
		   data = new byte[bb.limit()-bb.position()];
		   bb.get(data);
	   }
	   return concat(IntToByteArray(sequence), data);
   }
   
    
}
