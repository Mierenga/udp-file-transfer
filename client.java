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
            
            InetAddress ipaddr = InetAddress.getByName("");
		    try {
			    ipaddr = InetAddress.getByName(args[0]);	
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
            if (port > 65535 || port < 1) {
                System.err.println("second argument: port must be between 0 and 65536");
            }
        
            DatagramSocket clientSocket = new DatagramSocket(port+1);
            clientSocket.setSoTimeout(5000);
        
            // send request to the server
            InetAddress localAddr = InetAddress.getLocalHost();
            
            String filename = args[2]+" "+localAddr.getHostAddress();
            byte[] data = new byte[1500];
            data=filename.getBytes();
            DatagramPacket sendPacket = 
                new DatagramPacket(data,data.length,ipaddr,port);
            clientSocket.send(sendPacket);
            
            // Receive confirmation
            DatagramPacket recvPacket = 
                new DatagramPacket(data,data.length);
            clientSocket.receive(recvPacket);
            System.out.println(new String(recvPacket.getData()));
            
          
            
            
            
        
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
