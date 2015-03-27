/*
    Server for reliable file transfer over UDP
    Dan Hoover, Carson Schaefer, Mike Swierenga
    CIS 457: proj 3
*/


import java.io.*;
import java.net.*;
import java.lang.Integer;

class server {
    
    public static final int SIZE = 1500;
    
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
        if (port > 65535 || port < 1) {
            System.err.println("port must be between 0 and 65536");
            System.exit(0);
        }
        
    
        /* Open a socket to listen */
        try {
			DatagramSocket serverSocket = new DatagramSocket(port);
            byte[] data = new byte[SIZE];
            DatagramPacket recvPacket = 
                new DatagramPacket(data,data.length);
            serverSocket.receive(recvPacket);
            String requestString = new String(recvPacket.getData());
            String[] requestArray = requestString.split(" ");
            String fileName = "file request recieved: " + requestArray[0];
            String IPAddr = requestArray[1];
            System.out.println(fileName);
            System.out.println("IP: " + IPAddr);
            InetAddress clientIP = InetAddress.getByName(IPAddr);
            data = fileName.getBytes();
            fileName = requestArray[0];
            DatagramPacket sendPacket = new DatagramPacket(data,data.length,
					clientIP,port+1);
            serverSocket.send(sendPacket);
           
        
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
