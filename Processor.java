/** Processor
 *  Processor is the part of heimdahl where work happens: a processor is constructed and run
 * Created by serenity on 4/28/16.
 */


import jdk.nashorn.internal.parser.JSONParser;
import jdk.nashorn.internal.runtime.Context;

import java.io.*;
import java.lang.ClassLoader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.sound.sampled.*;

public class Processor implements Runnable{
    private int aMax = 100;
    private int maxBytes = 1000;
    private String songName;
    private String [] args;
    private String filterName;
    private String returnAdress;
    private InetAddress callback;

    private String JID;
    private Socket s;

    public Processor(Socket s){
        this.s=s;
    }

    public Processor(String songName, String [] args, String filterName, String returnAdress, String JID){
        this.songName=songName;
        this.args= args.clone();
        this.filterName = filterName;
        this.returnAdress=returnAdress;
        this.JID=JID;
    }

    public void collectArgs(){
        try {

            callback = s.getInetAddress();
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintStream out = new PrintStream(new BufferedOutputStream(s.getOutputStream()));
            String args="";
            int contentLength=0;
            int attempt=0;
            while(attempt<aMax) {
                args= in.readLine();
                System.out.println(args);
                if(args.toLowerCase().contains("content-length")) {
                    contentLength =Integer.parseInt(args.split(" ")[1]);
                }
                if (args.equals(""))
                    break;
                attempt++;
            }
            if(contentLength>maxBytes){
               //TODO: respond if failing entry? no this isn't a real server.
            }


            char [] chars = new char[contentLength];
            in.read(chars,0,contentLength);
            args = new String(chars);
            System.out.println(args.substring(0,100));
            String [] argVs = args.split(" ");
            in.close();
            out.close();
            s.close();



            JID = argVs[0];
            songName = argVs[1];
            returnAdress = argVs[2];
            filterName = argVs[3];
            this.args= new String[argVs.length-4];
            System.arraycopy(argVs,4,this.args,0,argVs.length-4);

        } catch(IOException e){
            System.out.println(e);
            //TODO handle socekt read error
        }
    }

    public void process(){
        ClassLoader cl = java.lang.ClassLoader.getSystemClassLoader();
        try {
            Class filter = cl.loadClass(filterName);
            Class fc = Class.forName("Filter"); //get the Filter interfaces class
            Class[] fimpl = filter.getInterfaces(); // list of interfaces implmented by the specefied filter
            boolean found = false;
            for (Class c : fimpl){
                if(c.equals(fc)){
                    found=true; //verify that the filter extends Filter
                    break;
                }
            }
            if(!found){
                throw new java.lang.ClassNotFoundException("Could not find required interface Filter");
                //otherwise throw an error
            }


            Filter f = ((Filter)filter.newInstance()); //At this point we have safely loaded a filter object using the filterName.
            //TODO: figure out formats supported
            try {
                //File audioFile= new File(songName); //load the songfile TODO:switch to remote loading from s3 + loading verification loop
                URL url = new URL(songName);
                url.openConnection();
                System.out.println("UrlOpen");
                AudioInputStream as = AudioSystem.getAudioInputStream(new BufferedInputStream(url.openStream()));
                System.out.println("Audio Stream Get");
                AudioFormat asf = as.getFormat();
                AudioFormat decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        asf.getSampleRate(),
                        16,
                        1, //force it to downsample to one channel
                        2, //window is now size 2
                        asf.getSampleRate(),
                        false);
                AudioInputStream asd = AudioSystem.getAudioInputStream(decodedFormat, as); //extract the data as a 16 bit stream

                double[] sf = new double[(int) asd.getFrameLength()];
                double[] stor = new double[(int) asd.getFrameLength()];//Note this system forces the audio length to be less than 27 hours at 44.1 khz (2^32/(44.1*1000)/60/60/24)
                byte[] frame = new byte[2]; //byte array to store a single frame of data
                int cf = 0; //cf is current frame
                while (asd.read(frame) > 0) {
                    int fv = 0; // frame value
                    fv |= (frame[0] & 0xFF); //unpack lower 8 bytes w/out sign extension
                    fv |= (frame[1]) << 8; //unpack upper 8 bytes, w/sign extension.

                    sf[cf++] = fv / ((double) (2 << 15)); //store the sample as a normalized value [-1,1)
                }
                stor = f.process(sf, args);
                //TODO: Save result into the cloud holder, get return data
                System.out.println("WavWriteStart");
                writeWav(stor,decodedFormat);
                System.out.println("RespWriteStart");
                //TODO: Foreward the saved result to the reutrn address
                URL callHeremes = new URL(Core.HermesUrl);
                HttpURLConnection con = (HttpURLConnection)callHeremes.openConnection();
                con.setDoOutput(true);
                con.setInstanceFollowRedirects(false);
                con.setRequestMethod("POST");
                con.setRequestProperty( "Content-Type", "application/json");
                con.setRequestProperty( "charset", StandardCharsets.UTF_8.name());
                String outString= "{\"authToken\": \"CHANGE THIS TO YOUR SECRET KEY\", \"jobID\": \""+JID+"\"}";
                con.setRequestProperty( "Content-Length", Integer.toString(outString.length()));
                con.setUseCaches( false );
                DataOutputStream ds = new DataOutputStream( con.getOutputStream());
                ds.writeBytes(outString);
                ds.flush();
                ds.close();
                int resp=con.getResponseCode();
                System.out.println("RespWriteEnd");

                /*
            Return protocol: dial returnAddress
            issue a "finished" call for "JID", attach saved cloud holder.
             */
            }catch (javax.sound.sampled.UnsupportedAudioFileException e){
                //TODO: Handle unspported audio error
                System.out.println(e);
            }catch (java.io.IOException e) {
                System.out.println(e);
                //TODO: Handle IO exeception
            } //TODO: Finaly's on audio?

        } catch(java.lang.ClassNotFoundException e){
            System.out.println(e);
            //TODO: choose a response method for filter which does not exist, or does not implment Filter
        }catch(java.lang.InstantiationException e){
            System.out.println(e);
            //TODO: choose a response method for filter which has no default constructor
        }catch(java.lang.IllegalAccessException e) {
            System.out.println(e);
            //TODO: choose a response method for filter which has no constructor
        }//TODO: finally on class load?
    }

    // convert a short to a byte array
    public static byte[] shortToByteArray(short data) {
        return new byte[]{(byte) (data & 0xff), (byte) ((data >>> 8) & 0xff)};
    }
    // convert an int to a byte array
    public static byte[] intToByteArray(int data) {
        return new byte[]{(byte) (data & 0xff), (byte) ((data >>> 8) & 0xff),(byte) ((data>>>16) & 0xff), (byte) ((data >>> 24) & 0xff)};
    }
    public boolean writeWav(double [] samples, AudioFormat af){
        try {
            int subChunk2sz = samples.length*af.getFrameSize();

            URL upload = new URL(returnAdress);
            HttpURLConnection con = (HttpURLConnection)upload.openConnection();
            con.setDoOutput(true);
            con.setInstanceFollowRedirects(false);
            con.setRequestMethod("PUT");
            con.setRequestProperty( "Content-Type", "audio/wav");
            con.setRequestProperty( "charset", StandardCharsets.UTF_8.name());
            con.setRequestProperty( "Content-Length", Integer.toString(44+subChunk2sz));
            con.setUseCaches( false );

//            File f = new File(new Integer(JID).toString() + "output.wav");
            DataOutputStream ds = new DataOutputStream( con.getOutputStream());


            // write the wav file per the wav file format
            ds.writeBytes("RIFF");                 // 00 - RIFF
            ds.write(intToByteArray(36+subChunk2sz), 0, 4);     // 04 - how big is the rest of this file?
            ds.writeBytes("WAVE");                 // 08 - WAVE
            ds.writeBytes("fmt ");                 // 12 - fmt
            ds.write(intToByteArray(16), 0, 4); // 16 - size of this chunk
            ds.write(shortToByteArray((short) 1), 0, 2);        // 20 - what is the audio format? 1 for PCM = Pulse Code Modulation
            ds.write(shortToByteArray((short) 1), 0, 2);  // 22 - mono or stereo? 1 or 2?  (or 5 or ???)
            ds.write(intToByteArray((int) af.getSampleRate()), 0, 4);        // 24 - samples per second (numbers per second)
            ds.write(intToByteArray((int) af.getSampleRate()*af.getFrameSize()),0,4);
            ds.write(shortToByteArray((short) af.getFrameSize()), 0, 2);    // 32 - # of bytes in one sample, for all channels
            ds.write(shortToByteArray((short) af.getSampleSizeInBits()), 0, 2); // 34 - how many bits in a sample(number)?  usually 16 or 24
            ds.writeBytes("data");                 // 36 - data
            ds.write(intToByteArray(subChunk2sz), 0, 4);      // 40 - how big is this data chunk



            //begin decode
            byte[] frame = new byte[2];
            for(double sample: samples) {
                int fv = (int)(sample*((double) (2 << 15)));
                frame[0] =  (byte)(fv & 0xFF);
                frame[1] =  (byte)((fv>>8) & 0xFF); //check I guess?
                ds.write(frame,0,2);// 44 - the actual data itself - just a long string of numbers
            }
            ds.flush();
            ds.close();
            int rsp = con.getResponseCode();
            System.out.println(con.getResponseCode());
            System.out.println(con.getResponseMessage());
        }catch(java.io.IOException e){
            System.out.println("WavWriteErr "+e);
            //TODO: io failure
        }

        return false;
    }

    @Override
    public void run() {
        collectArgs();
        process();
    }

    public static void main(String[] args){
        Processor p = new Processor("test.wav", new String[]{"4.0"},"DampingFilter","nonce","10001");
        Thread th = new Thread(p, "job10001");
        th.start();
    }
}


