import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.commons.net.ntp.TimeStamp;
import org.apache.commons.net.ntp.NtpV3Packet;

import java.net.InetAddress;

public class NTPClient {

    public static void main(String[] args) {
        // Lista de servidores NTP de ejemplo
        String[] ntpServers = {
                "time.windows.com",
                "pool.ntp.org",
                "time.google.com",
                "time.nist.gov"
        };

        for (String server : ntpServers) {
            System.out.println("=== Consultando servidor NTP: " + server + " ===");
            consultarHoraServidor(server);
            System.out.println();
        }
    }

    public static void consultarHoraServidor(String hostname) {
        NTPUDPClient client = new NTPUDPClient();
        client.setDefaultTimeout(10000); // Timeout de 10 segundos

        try {
            client.open();
            InetAddress hostAddr = InetAddress.getByName(hostname);
            TimeInfo info = client.getTime(hostAddr);
            info.computeDetails(); // Calcula offset y delay

            NtpV3Packet message = info.getMessage();

            TimeStamp ref = message.getReferenceTimeStamp();
            TimeStamp orig = message.getOriginateTimeStamp();
            TimeStamp recv = message.getReceiveTimeStamp();
            TimeStamp xmit = message.getTransmitTimeStamp();
            TimeStamp ret = TimeStamp.getNtpTime(info.getReturnTime());

            System.out.println("Reference Timestamp : " + ref.toDateString());
            System.out.println("Originate Timestamp : " + orig.toDateString());
            System.out.println("Receive Timestamp   : " + recv.toDateString());
            System.out.println("Transmit Timestamp  : " + xmit.toDateString());
            System.out.println("Return Timestamp    : " + ret.toDateString());

            Long delay = info.getDelay();
            Long offset = info.getOffset();

            System.out.println("Delay (ms)          : " + (delay != null ? delay : "N/A"));
            System.out.println("Offset (ms)         : " + (offset != null ? offset : "N/A"));
        } catch (Exception e) {
            System.err.println("Error al consultar el servidor " + hostname + ": " + e.getMessage());
        } finally {
            client.close();
        }
    }
}
