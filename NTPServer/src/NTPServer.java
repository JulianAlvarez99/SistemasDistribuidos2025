import org.apache.commons.net.ntp.NtpV3Impl;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeStamp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class NTPServer {

    private static final int NTP_PORT = 6000;

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(NTP_PORT)) {
            System.out.println("Servidor NTP activo en puerto " + NTP_PORT);

            byte[] buffer = new byte[48];

            while (true) {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                socket.receive(request);

                long receiveTime = System.currentTimeMillis();
                NtpV3Packet incomingMessage = new NtpV3Impl();
                incomingMessage.setDatagramPacket(request);

                NtpV3Impl response = new NtpV3Impl();
                response.setMode(NtpV3Packet.MODE_SERVER);
                response.setStratum(1); // reloj principal (stratum 1)
                response.setLeapIndicator(0);

                response.setOriginateTimeStamp(incomingMessage.getTransmitTimeStamp());
                response.setReceiveTimeStamp(TimeStamp.getNtpTime(receiveTime));
                response.setReferenceTime(response.getReceiveTimeStamp());
                response.setTransmitTime(TimeStamp.getNtpTime(System.currentTimeMillis()));

                DatagramPacket reply = response.getDatagramPacket();
                reply.setPort(request.getPort());
                reply.setAddress(request.getAddress());

                socket.send(reply);
                System.out.println("Respondió a " + request.getAddress().getHostAddress() + ":" + request.getPort());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
