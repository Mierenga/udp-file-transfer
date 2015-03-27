/*
    Client for reliable file transfer over UDP
    Dan Hoover, Carson Schaefer, Mike Swierenga
    CIS 457: proj 3
*/

import java.io.*;
import java.net.*;
import java.util.*;

class client {

    public static void main(String args[]) {
        
        if (args.length != 3) {
            System.err.println("usage:\n" +
                               "\t1st arg : IP address of server\n" +
                               "\t2nd arg : port of server\n" + 
                               "\t3rd arg : name of file to obtain");
           System.exit(0);
        }
        
        
        
        /* Open a socket to the server */
        try {
        
            /* Get IP addr of the server */
            
            InetAddress serverAddr = InetAddress.getByName("");
		    try {
			    serverAddr = InetAddress.getByName(args[0]);	
		    } catch (ArrayIndexOutOfBoundsException e) {
			    System.out.println("first argument: invalid IP address");
			    System.exit(0);
	        } 
	        
            /* Get port of the server */
            
            int port = 0;
            try {
                port = (Integer.parseInt(args[1]));
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("second argument: invalid port number");
                System.exit(0);
            } catch (NumberFormatException e) {
                System.err.println("second argument: invalid port number");
                System.exit(0);
            }
            if (port > 65535 || port < 1024) {
                System.err.println("second argument: port must be from 1024 to 65535");
            }
        
            DatagramSocket clientSocket = new DatagramSocket();
            clientSocket.setSoTimeout(5000);
        
            /* send request to the server */
            
            byte[] filenameData = args[2].getBytes();
            
            DatagramPacket sendPacket = 
                new DatagramPacket( filenameData, filenameData.length, serverAddr, port );
            clientSocket.send(sendPacket);
            
            /* Receive confirmation */
            
            byte[] confirmationData = new byte[1500];
            
            DatagramPacket recvPacket = 
                new DatagramPacket( confirmationData, confirmationData.length );
            clientSocket.receive( recvPacket );
            
            String confirmation = new String( recvPacket.getData() );
            System.out.println( confirmation );
            
          
            
            
            
        
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

}
