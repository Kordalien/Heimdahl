import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * Created by serenity on 5/22/16.
 * Heimdal-Core implments the client server architecture
 *
 */
public class Core {
    private static ServerSocket socket;
    public static String HermesUrl;
    public static int ct;
    public static void main(String[] args){
        ct=0;
        HermesUrl = "https://hermes-1302.appspot.com/_ah/api/hermes/v1/heimdallUpdateJobStatus";
        if(args.length>1)
            HermesUrl=args[1];
        try{
            socket = new ServerSocket(8008);  //Get a socket on port 80; standard connection port.
            while(true) {
                System.out.println("Hats");
                Socket s = socket.accept();
                Thread Th = new Thread( new Processor(s));
                Th.start();
            }
        }catch(IOException e){
            //TODO log ioexception to log.
            System.out.println(e);
        }
    }
}
