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
import java.lang.*;


public class server {
    
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
                
                byte[] data = new byte[Constants.PACK_SIZE];

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
                	
		    // Get info about the file
                	
		    double dFileSize = fileChannel.size();

		    int totalPackets = (int) Math.ceil(dFileSize / Constants.DATA_SIZE);
            int fileSize = (int) dFileSize;
                	
		    // Send the confirmation of request
                	
		    serverSocket.send (
                assembleConfirmPacket (
                    fileName, fileSize, totalPackets, clientAddr, clientPort));

            // Setup variables for sequencing and acknowledging                    
                    
		    int sequence = 0;
		    int acknowledgment = 0;
		    int ackCount = 0;
                	
            Window window = new Window(Constants.WINDOW_SIZE);
		    window.printWindow();
                	
            int head = 0;
                	
            // Construct and send the first five packets

            System.out.print("packet traffic:\n[");

                    for (int i = 0; i < Constants.WINDOW_SIZE; i++) {
                        
                        if (sequence < totalPackets) {
                            
                            serverSocket.send(
                                constructNextPacket(
                                    fileChannel, clientAddr, clientPort, sequence));
                            
                            window.loadFirstEmpty(sequence);

                            System.out.print("s:" + sequence + ", ");
                            sequence++;
                            
                        } else {
                            break;
                        }
                        
                    }
                window.printWindow();
                // Fire off a TimoutThread to check for packet losses
	                
                TimeoutThread timeoutThread = new TimeoutThread(serverSocket, clientAddr, clientPort, fileChannel, window);
                timeoutThread.start();
	                
	                
                // listen for acknowledgments from client
                
                boolean complete = false;
                boolean[] acksRcvd = new boolean[totalPackets];
    
                while (!complete) {
                    
    			// listen for any ack
            	System.out.println("\nlistening..");		
    			serverSocket.receive(recvPacket);
                System.out.println("received");
    			// parse ack for sequence number
            			
    			acknowledgment = getAckNumber(recvPacket);
                if (acksRcvd[acknowledgment] == false) {
    			    acksRcvd[acknowledgment] = true;
                }
                        
                        System.out.print("a:" + acknowledgment + ", ");
                        
                        // update window with new acknowledgment,
                        //     find how many new packets to send from the return value
                        
                        int packetsToSend = window.recvAck(acknowledgment);
                        window.printWindow();
                        System.out.println("toSend: " + packetsToSend);
                        
                        // If necessary, send new packets and load them into the window
                        
                        for (int i = 0; i < packetsToSend; i++) {
			
                            serverSocket.send(
                                constructNextPacket(
                                    fileChannel, clientAddr, clientPort, sequence));
                        
                            window.loadFirstEmpty(sequence);
                            
                            System.out.print("s:" + sequence + ", ");
                            sequence++;
                            
                        }
                        System.out.println("A");
                        int count = 0;
                        for (boolean a : acksRcvd) {
                            if (!a) {
                                return;
                            } else {
                                count++;
                            }
                        }
                        if (count == totalPackets) {
                            complete = true;
                        }
                        System.out.println("B");
                    }
            	    
            	    timeoutThread.kill();
            	    
	                System.out.println("client acknowledged " +
                            ackCount + " of " + totalPackets + " packets]\n");
	            	
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
        Returns a DatagramPacket with 1000 bytes of file data
        and a complete header, ready to be sent
    */
    public static DatagramPacket
    constructNextPacket(SeekableByteChannel sbc, InetAddress addr, int port, int seq)
    {
        ByteBuffer buf = ByteBuffer.allocate(Constants.DATA_SIZE);
        try {
            sbc.read(buf);
        } catch (IOException e) {
            System.err.println(e);
        }
        
        
        // TODO part 3: compute the checkSum of the buf and seq here
        computeChecksum(seq, buf);
        
        byte[] data = new byte[Constants.PACK_SIZE];
        data = addHeader(seq, buf);
        
        return new DatagramPacket(data, data.length, addr, port);
    }
    /*
        TODO part 3: include the checkSum in the header construction
        
        Helper for constructNextPacket()
        Returns a byte array with header and data, ready to be loaded into a packet
        Packet includes:
            4 bytes of sequence number
            TODO (Up to) 20 bytes of checksum
            1000 bytes data
    */
    public static byte[]
    addHeader(int seq, ByteBuffer data)
    {
        byte[] packArr = new byte[Constants.PACK_SIZE];
        byte[] headerArr = ByteBuffer.allocate(Constants.HEAD_SIZE).putInt(seq).array();
        
        System.arraycopy(headerArr, 0, packArr, 0, Constants.HEAD_SIZE);
        System.arraycopy(data.array(), 0, packArr, 4, Constants.DATA_SIZE);
        
        return packArr;
    }
    /*
        TODO part 3
        Returns the checksum for the given seq# and data buffer
    */
    public static byte[]
    computeChecksum(int seq, ByteBuffer buf)
    {
        return null;
    }

    /*
        Returns sequence number from data of acknowledgment packet
    */
    public static int
    getAckNumber(DatagramPacket dp)
    {
	ByteBuffer ack = ByteBuffer.allocate(4).put(dp.getData(), 0, 4);
        ack.flip();
        return ack.getInt();
    }
}

// TimoeoutThread is a helper thread that continuously
// checks each of the values in timeSent[]:
//   If more than TIMEOUT seconds has occurred since
//   any timeSent, we should resend the packet
//   in that slot of the window.
    
class TimeoutThread extends Thread {

    
    private volatile boolean isRunning = true;
    
    private DatagramSocket socket = null;
    
    private SeekableByteChannel fileChannel;
    private int clientPort;
    private InetAddress clientAddr;
    private Window window;
    
    
    
    public TimeoutThread(DatagramSocket sock, InetAddress a, 
                            int p, SeekableByteChannel sbc, Window win)
    {
        this.socket = sock;
        this.fileChannel = sbc;
        this.clientPort = p;
        this.clientAddr = a;
        this.window = win;
    }
    
    public void
    run() {
        while (isRunning) {
        
            try {

                for (int i = 0; i < Constants.WINDOW_SIZE; i++) {
                    if (window.getSeqNumber(i) > -1) {
                        if ((System.currentTimeMillis() - window.getTimeSent(i)) > 
                                Constants.ACK_TIMEOUT ) {
                        
                            int sequence = window.getSeqNumber(i);
                            
                            socket.send(
                                server.constructNextPacket(
                                    fileChannel, clientAddr, clientPort, sequence));
                                    
                            window.updateTimeSent(sequence);
                            
                        }
                    }
                }
                
            } catch (SocketException x) {
                System.err.println("Unable to use timeout " + x);
            } catch (IOException x) {
                System.err.println(x);
            }
            
        }
    }
    
    public void
    kill() {
        isRunning = false;
    }
    
}

















