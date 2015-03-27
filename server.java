/*
    Server for reliable file transfer over UDP
    Dan Hoover, Carson Schaefer, Mike Swierenga
    CIS 457: proj 3
*/


import java.io.*;
import static java.nio.file.StandardOpenOption.*;
import java.nio.file.*;
import java.net.*;
import java.lang.Integer;

class server {
    
    public static final int SIZE = 1500;
    
    public static void main(String args[]) {
    
    
        /* Set up a log file */
        
        

    
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
            String filename = new String(recvPacket.getData());
            filename = filename.trim();
            
            /* Send a confirmation of request */
            
            String confirmation = new String("server received request for '" + filename + "'");
            InetAddress clientAddr = recvPacket.getAddress();
            int clientPort = recvPacket.getPort();
            System.out.println( confirmation + " from " + clientAddr.toString() + " [" + clientPort + "]" );
            data = confirmation.getBytes();
            DatagramPacket sendPacket =
                new DatagramPacket(data, data.length, clientAddr, clientPort);
            serverSocket.send(sendPacket);
            
            /* Begin sending the file data */
           
        
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
    
}
