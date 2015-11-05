import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Random;

class Client
{
   DatagramSocket clientSocket;

   InetAddress ClientIP;
   int ClientPort;
   
   InetAddress ServerIP;
   int ServerPort;

   int CurrentSeqNumber;
   int CurrentAckNumber;

   int AdvertisedWindow;

   public Client(InetAddress ClientIP, InetAddress ServerIP, int ServerPort) throws SocketException, UnknownHostException
   {
      Random rand = new Random();
      int RandomPort = 9000 + rand.nextInt((1000) + 1);

      this.clientSocket = new DatagramSocket(RandomPort);

      this.ClientIP = ClientIP;
      this.ClientPort = RandomPort;

      this.ServerIP = ServerIP;
      this.ServerPort = ServerPort;

      this.CurrentSeqNumber = 1;
      this.CurrentAckNumber = 1;

      this.AdvertisedWindow = 1;
   }

   public void SendPacket(String stringData, Boolean[] ControlBit) throws IOException
   {
      byte[] sendData = new byte[1024];

      TCPPacket pkt = new TCPPacket(ClientIP, ClientPort, ServerIP, ServerPort, CurrentSeqNumber, CurrentAckNumber , AdvertisedWindow, ControlBit, stringData);
      sendData = pkt.getStringPacket().getBytes();

      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ServerIP, ServerPort);
      clientSocket.send(sendPacket);
   }

   public TCPPacket ReceivePacket() throws IOException
   {
      byte[] receiveData = new byte[1024];

      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      clientSocket.receive(receivePacket);
      String stringData = new String(receivePacket.getData());

      TCPPacket revpkt = new TCPPacket(stringData);
      return revpkt; 
   }

   public void SendAck() throws IOException
   {
      // Send Ack
      String stringpkt = Integer.toString(CurrentAckNumber);
      Boolean[] ControlBit = new Boolean[6];
      Arrays.fill(ControlBit, Boolean.FALSE);
      ControlBit[1] = true;

      SendPacket(stringpkt, ControlBit);
   }

   public void Handshake() throws IOException
   {
      // Send Syn
      String stringpkt = "Syn";

      Boolean[] ControlBit = new Boolean[6];
      Arrays.fill(ControlBit, Boolean.FALSE);
      ControlBit[4] = true;

      SendPacket(stringpkt, ControlBit);

      // Receive SYN + ACK

      boolean GotSynAck = false;
      while(!GotSynAck)
      {
         TCPPacket revpkt = ReceivePacket();
         ControlBit = revpkt.getControlBit();

         if(ControlBit[4] == true && ControlBit[1] == true)
         {
            GotSynAck = true;
            System.out.println("Got SYN+ACK");
         }   
      }

      CurrentSeqNumber = 1;
      CurrentAckNumber = 1;
      AdvertisedWindow = 1;

      SendAck();
   }

   public void ReceiveData() throws IOException
   {
      TCPPacket revpkt = ReceivePacket();
      Boolean[] ControlBit = revpkt.getControlBit();

      while(!ControlBit[5]) // While we do get FIN, keep on receiving packets
      {
         if(!ControlBit[1] && !ControlBit[4]) // This implies that it is a regular data packet
         {
            this.CurrentSeqNumber = revpkt.getSeqNumber();

            String receiveData = revpkt.getdata();

            if(CurrentSeqNumber == CurrentAckNumber + 1)
               CurrentAckNumber = CurrentSeqNumber;
            // Send ACK for received packet
            SendAck();

            System.out.println("Received: " + receiveData);
            System.out.println("Packets received: " + (CurrentAckNumber - 1));
         }

         try
         {
            revpkt = ReceivePacket();
         }
         catch (Exception E)
         {
            System.out.println("Sending Timeout Ack");
            SendAck();
         }

         ControlBit = revpkt.getControlBit();

         //System.out.println("No FINs yet!");
      }

      System.out.println("Out of the Loooop");
   }

   public static void main(String args[]) throws Exception, UnknownHostException
   {
      InetAddress ServerIP = InetAddress.getByName("localhost");
      InetAddress ClientIP = ServerIP;
      int ServerPort = 9999;

      Client thisClient = new Client(ClientIP, ServerIP, ServerPort);

      thisClient.clientSocket.setSoTimeout(10); 

      thisClient.Handshake();
      thisClient.ReceiveData();

      System.out.println("Finished! Yay!");

      thisClient.clientSocket.close();
   }
}