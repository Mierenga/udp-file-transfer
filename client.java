/*
    Client for reliable file transfer over UDP
    Dan Hoover, Carson Schaefer, Mike Swierenga
    CIS 457: proj 3
*/

import java.io.*;
import java.net.*;

class client {

    public static final int SIZE = 1004;
    public static final int TIMEOUT = 5000;

    public static void main(String args[]) {
        
        if (args.length != 3) {
            System.err.println("usage:\n" +
                               "\t1st arg : IP address of server\n" +
                               "\t2nd arg : port of server\n" + 
                               "\t3rd arg : name of file to obtain");
           System.exit(0);
        }
        
        try {
        
            /* Get IP addr of the server */
            
            InetAddress serverAddr = null;
            
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
            
            /* Open a socket to the server */
             
            DatagramSocket clientSocket = new DatagramSocket();
            clientSocket.setSoTimeout(TIMEOUT);
        
            /* send request to the server */
            
            byte[] filenameData = args[2].getBytes();
            
            DatagramPacket sendPacket = 
                new DatagramPacket( filenameData, filenameData.length, serverAddr, port );
            clientSocket.send(sendPacket);
            
            /* Receive confirmation or denial */
            
            byte[] recvData = new byte[SIZE];
            
            DatagramPacket recvPacket = 
                new DatagramPacket( recvData, recvData.length );
            clientSocket.receive( recvPacket );
            
            String status = new String( recvPacket.getData() );
            System.out.println( status );
            
            if (confirmation.contains("unable")) {
                clientSocket.close();
            	System.exit(1);
            }
	        
            // Create a path for output file
            
        	Path path = Paths.get("new-transferred-file.out");
            
            // Create a SeekableByteChannel object for the path
	        	
	        SeekableByteChannel fileChannel = Files.newByteChannel(path, EnumSet.of(CREATE, WRITE));
            
            int acknowledgmentNumber = 0;
            
                // receive packet of data
                
                recvPacket = 
                    new DatagramPacket( recvData, recvData.length );
                clientSocket.receive( recvPacket );
                
                // write the packet contents to the appropriate spot in SeekableByteChannel
                
                
                acknowledgmentNumber = writeToChannel(recvPacket.getData(), fileChannel);
                
                // send acknowledgment number to server
                
                
                
                
            
            clientSocket.close();
        
        } catch (UnknownHostException e) {
            System.out.println("first argument: invalid IP address");
		    System.exit(1);
	    } catch (SocketTimeoutException e ) {
            System.err.println("No response from server.");
            System.exit(1);
        } catch (SocketException e) {
            System.err.println("Socket error: " + e.getMessage());
			System.exit(1);
        } catch (IOException e) {
			System.err.println("IO error: " + e.getMessage());
			System.exit(1);
		}
        
    }
    
    /*
        Returns the sequence number of the data being written
    */
    public static int
    writeToChannel(byte[] packet, SeekableByteChannel sbc)
    {
        // put packet data into ByteBuffer bb
        // store packet head into int x
        // write bb to sbc at position (x*DATA_SIZE)
        
    }

}
