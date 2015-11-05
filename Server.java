import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Random;

class TimerPair
{
   public int SequenceNumber;
   public long Timeout;

   public TimerPair(int SequenceNumber, Timeout)
   {
      this.SequenceNumber = SequenceNumber;
      this.Timeout = Timeout;
   }
}

public class Server
{
   DatagramSocket serverSocket;
   
   InetAddress ServerIP;
   int ServerPort;

   InetAddress ClientIP;
   int ClientPort;

   int CurrentSeqNumber;
   int CurrentAckNumber;

   boolean CurrentFinish;

   float CongestionWindow;
   int SSThreshold;

   ArrayList<String> PacketList;
   int FixedPacketSize;

   ArrayList<TimerPair> TimerQ;

   // Code for generating random Strings for packets
   static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
   static Random rnd = new Random();

   String randomString( int len ){
   StringBuilder sb = new StringBuilder( len );
   for( int i = 0; i < len; i++ ) 
      sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
   return sb.toString();
   }

   void createPacketList()
   {
      PacketList = new ArrayList<String>();
      for(int i = 0; i < 1000; i++)
         PacketList.add(randomString(FixedPacketSize));
   }

   void printPacketList()
   {
      for(int i = 0; i < PacketList.size(); i++)
         System.out.println(PacketList.get(i));
   } 

   public Server(InetAddress ServerIP, int ServerPort) throws SocketException
   {
      // Create Server Socket
      this.serverSocket = new DatagramSocket(ServerPort);

      this.ServerIP = ServerIP;
      this.ServerPort = ServerPort; 

      CurrentSeqNumber = 1;
      CurrentAckNumber = 1;
      CurrentFinish = false;

      CongestionWindow = 1;
      SSThreshold = 30;

      FixedPacketSize = 5;

      // Create data that the server transmits on request
      createPacketList();
      //printPacketList();

      TimerQ = new ArrayList<TimerPair>();
   }

   public void SendPacket(String stringData, Boolean[] ControlBit) throws IOException
   {
      byte[] sendData = new byte[1024];

      int SequenceNumber = 1;
      int AckNumber = 1;

      TCPPacket pkt = new TCPPacket(ServerIP, ServerPort, ClientIP, ClientPort, CurrentSeqNumber, CurrentAckNumber , (int)CongestionWindow, ControlBit, stringData);
      sendData = pkt.getStringPacket().getBytes();

      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ClientIP, ClientPort);
      serverSocket.send(sendPacket);
   }

   public TCPPacket ReceivePacket() throws IOException
   {
      // Check whether this packet is for us

      byte[] receiveData = new byte[1024];

      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      serverSocket.receive(receivePacket);
      String stringData = new String(receivePacket.getData());

      TCPPacket revpkt = new TCPPacket(stringData);
      return revpkt; 
   }

   public void Handshake(InetAddress ClientIP, int ClientPort) throws IOException
   {
      // Send Syn+Ack
      Boolean[] ControlBit = new Boolean[6];
      Arrays.fill(ControlBit, Boolean.FALSE);
      ControlBit[4] = true;
      ControlBit[1] = true;

      String stringpkt = "Syn+Ack";

      SendPacket(stringpkt, ControlBit);

      // Receive Ack

      Boolean gotSyn = false;
      while(!gotSyn)
      {
         TCPPacket revpkt = ReceivePacket();
         ControlBit = revpkt.getControlBit();

         if(ControlBit[1])
         {
            gotSyn = true;
            System.out.println("Got SYN");
         }

      }

      CurrentSeqNumber = 1;
      CurrentAckNumber = 1;
      CurrentFinish = false;

      CongestionWindow = 1;
   }

   void incrementCW()
   {
      if((int)CongestionWindow < SSThreshold)
         CongestionWindow += 1.0;
      else
         CongestionWindow += 1.0/CongestionWindow;

      if(CongestionWindow > 100)
         CongestionWindow = 100;
   }

   void removeFromQueue(int acknum)
   {
      
   }


   public void ProcessPacket(TCPPacket revpkt) throws IOException
   {
      Boolean[] ControlBit = revpkt.getControlBit();

      // Get Address of client
      this.ClientIP = revpkt.getSourceIP();
      this.ClientPort = revpkt.getSourcePort();

      if(ControlBit[4])
      {
         Handshake(ClientIP, ClientPort);
      }
      else if(ControlBit[1])
      {
         int acknum = revpkt.getAckNumber();

         removeFromQueue(acknum);   

         incrementCW();

         if (CurrentAckNumber < revpkt.getAckNumber())
            CurrentAckNumber = revpkt.getAckNumber();
         
         System.out.println("Acknowledged Packets: " + (CurrentAckNumber - 1));
      }
   }

   public void SendNextPackets() throws IOException
   {
      int PacketNumberToSend = CurrentAckNumber - 1;

      System.out.println(PacketNumberToSend);

      for(int i = 0; (i < (int)CongestionWindow) && (PacketNumberToSend + i < PacketList.size()); i++)
      {
         String stringpkt;
         Boolean[] ControlBit = new Boolean[6];
         Arrays.fill(ControlBit, Boolean.FALSE);
         
         if(PacketNumberToSend + i < PacketList.size())
            stringpkt = PacketList.get(PacketNumberToSend + i);
         else // Send FIN
         {
            stringpkt = "FIN";
            ControlBit[5] = true;
            CurrentFinish = true;
         }
         
         this.CurrentSeqNumber = (PacketNumberToSend + 1) + 1 + i;

         SendPacket(stringpkt, ControlBit);
      }
   }

   public void SendFin() throws IOException
   {
      String stringpkt = "FIN";

      Boolean[] ControlBit = new Boolean[6];
      Arrays.fill(ControlBit, Boolean.FALSE);
      ControlBit[5] = true;

      SendPacket(stringpkt, ControlBit);
   }


   public static void main(String args[]) throws Exception, UnknownHostException
      {
         InetAddress ServerIP = InetAddress.getByName("localhost");
         int ServerPort = 9999;

         Server thisServer = new Server(ServerIP, ServerPort);

         thisServer.serverSocket.setSoTimeout(10000); 

         while(true)
         {
            // Receive Packet
            TCPPacket revpkt = thisServer.ReceivePacket();

            thisServer.ProcessPacket(revpkt);

            if(!thisServer.CurrentFinish)
               thisServer.SendNextPackets();
            else
               thisServer.SendFin();
         }
      }
} 