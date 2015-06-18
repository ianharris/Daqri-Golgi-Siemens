package io.golgi.modbusclient;

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Vector;

import com.openmindnetworks.golgi.api.*;
import net.wimpi.modbus.*;
import net.wimpi.modbus.msg.*;
import net.wimpi.modbus.io.*;
import net.wimpi.modbus.net.*;
import net.wimpi.modbus.util.*;

import io.golgi.daqri.gen.*;

/**
 * Created by ianh on 11/06/2015.
 *
 * The ModbusClient will create a persistent TCP connection to the Modbus server - in our case the Siemens Sentron
 * PAC3200. The hardware that is running the ModbusClient will be on the same network as the Siemens Sentron. The
 * ModbusClient will then await requests for readings from the DaqriHelmet process - which could be running on hardware
 * attached to a separate network to the Siemens Sentron.
 *
 * Golgi requests from the DaqriHelmet are received in the receiveFrom method of the DaqriService.read.RequestReceiver
 * readRequestReceiver. Within the receiveFrom method a request is sent on the Modbus connection. The retrieved data is
 * then bundled into a response and sent via Golgi to the DaqriHelmet.
 *
 */
public class ModbusClient {

    private static boolean golgiRegistered = false;
    private static TCPMasterConnection con = null; //the connection
    private static ArrayList<String> userList;
    private final static String usersFilename = "DaqriHelmet-Users.txt";
    private static GolgiTransportOptions gto = new GolgiTransportOptions();

    private static void usage(){
        System.out.println("Usage:");
        System.out.println("\tjava -cp CLASSPATH io.golgi.modbusclient.ModbusClient [hostname:port] [offset] [tabs]");
        System.out.println("Example - called from build/classes/main of the project should read the max frequency of Sentron PAC3200:");
        System.out.println("\tjava -cp .:../../../libs/jamod.jar io.golgi.modbusclient.ModbusClient SERVER_IP:502 129 2");
    }

    private static SentronData readModbusRegister(int index, int count) throws ModbusIOException, ModbusSlaveException,
                                                                         ModbusException, SentronReadException{
        SentronReadException sre;
        if(con == null){
            sre = new SentronReadException();
            sre.setError("No connection to Modbus server");
            throw sre;
        }

        // create a transaction, request and response object
        ModbusTCPTransaction trans;
        ReadMultipleRegistersRequest req = null;
        ReadMultipleRegistersResponse res = null;

        // initialise the request and transactions
        req = new ReadMultipleRegistersRequest(index,count);
        req.setUnitID(1);   // id must be set as some Modbus servers don't respond to default id 0xff
        trans = new ModbusTCPTransaction(con);
        trans.setRequest(req);
        trans.execute(); // execute the transaction

        // get the response
        res = (ReadMultipleRegistersResponse) trans.getResponse();

        // create the SentronData instance to package the output data
        SentronData sd = new SentronData();
        // set the first index
        sd.setIndex(index);

        // create a byte array to store the data
        byte[] data = new byte[res.getByteCount()];
        for(int i = 0;
            i < res.getWordCount();
            i++){

            byte[] tmp = res.getRegister(i).toBytes();
            data[2*i]  = tmp[0];
            data[2*i+1] = tmp[1];
        }
        // set the SentronData instances data
        sd.setRegisterData(data);

        return sd;
    }

    // receive Modbus data read requests
    private static DaqriService.read.RequestReceiver readRequestReceiver = new DaqriService.read.RequestReceiver() {
        @Override
        public void receiveFrom(DaqriService.read.ResultSender resultSender, SentronRequest sr) {

            SentronReadException sre;
            SentronData sd;

            try{
                sd = readModbusRegister(sr.getIndex(),sr.getNumberRegisters());
            }
            catch(ModbusIOException ex){
                System.out.println("ModbusIOException: " + ex.getMessage());
                sre = new SentronReadException();
                sre.setError("ModbusIOException: " + ex.getMessage());
                resultSender.failure(sre);
                return;
            }
            catch(ModbusSlaveException ex){
                System.out.println("ModbusSlaveException: " + ex.getMessage());
                sre = new SentronReadException();
                sre.setError("ModbusSlaveException: " + ex.getMessage());
                resultSender.failure(sre);
                return;
            }
            catch(ModbusException ex){
                System.out.println("ModbusException: " + ex.getMessage());
                sre = new SentronReadException();
                sre.setError("ModbusException: " + ex.getMessage());
                resultSender.failure(sre);
                return;
            }
            catch(SentronReadException srex){
                resultSender.failure(srex);
                return;
            }

            // resultsSender.success(sd) call returns the sd to the requester in the response
            resultSender.success(sd);
        }
    };

    private static class NotificationResultReceiver implements DaqriService.notify.ResultReceiver{

        private String target;

        NotificationResultReceiver(String target){
            this.target = target;
        }

        @Override
        public void failure(GolgiException ex){
            System.out.println("Failed to deliver notification to " + target);
        }

        @Override
        public void success(){
            System.out.println("Successfully delivered notification to " + target);
        }
    };

    private static void sendNotification(String not){
        DaqriNotification dn = new DaqriNotification();
        dn.setNot(not);
        for(String id: userList){
            DaqriService.notify.sendTo(new NotificationResultReceiver(id),
                                       gto,
                                       id,
                                       dn);
        }
    }

    private static void loadUsers(){
        try{
            BufferedReader br = new BufferedReader(new FileReader(usersFilename));
            userList = new ArrayList<String>();

            String id;
            while((id = br.readLine()) != null){
                userList.add(id);
            }
            System.out.println("Loaded " + userList.size() + " users");
        }
        catch(IOException ioex){
            System.out.println("LoadUsers: " + ioex.getMessage());
        }
    }

    private static void saveUsers(){
        try{
            BufferedWriter bw = new BufferedWriter(new FileWriter(usersFilename));
            Vector<String> v = new Vector<String>();
            synchronized(userList){
                for(String id: userList){
                    v.add(id);
                }
            }
            while((v.size() > 0)){
                String user = v.remove(0);
                bw.write(user  + "\n");
            }
            bw.close();
        }
        catch(IOException ioex){
            System.out.println("SaveUsers: " + ioex.getMessage());
        }

    }

    // receive helmet registration requests
    private static DaqriService.register.RequestReceiver registerRequestReceiver = new DaqriService.register.RequestReceiver() {
        @Override
        public void receiveFrom(DaqriService.register.ResultSender resultSender, DaqriRegister dr) {
            synchronized (userList){
                userList.add(dr.getUid());
                saveUsers();
                resultSender.success();
            }
        }
    };

    private static void golgiSetup(){

        // Static reference to load the class
        Class<GolgiAPI> apiRef = GolgiAPI.class;
        // Host and port of Golgi Servers
        GolgiAPI.setAPIImpl(new GolgiAPINetworkImpl());

        // setting a 60 second validity period on sends
        gto.setValidityPeriod(60);

        // register with id DAQRI_SERVER

        /*
         * uncomment the following lines if you are accepting registrations for notifications
         */
        // loadUsers();
        // DaqriService.register.registerReceiver(registerRequestReceiver);

        DaqriService.read.registerReceiver(readRequestReceiver);
        GolgiAPI.getInstance().register(GolgiKeys.DEV_KEY,
                                        GolgiKeys.APP_KEY,
                                        "DAQRI_SERVER",
                                        new GolgiAPIHandler() {

                                            @Override
                                            public void registerSuccess() {
                                                System.out.println("Registration Success");
                                                golgiRegistered = true;
                                            }

                                            @Override
                                            public void registerFailure() {
                                                System.out.println("Registration Failure");
                                            }
                                        });

        while(!golgiRegistered){
            try {
                Thread.sleep(1000);
            }
            catch(InterruptedException ex){
                System.out.println("Golgi Registration Interrupted Exception: " + ex.getMessage());
            }
        }
    }

    public static void main(String [] args) {

        // Modbus connection variables
        InetAddress addr = null; //the slave's address
        int port = Modbus.DEFAULT_PORT;

        // create the connection to the Modbus server
        try {
            if(args.length >= 1){
                if(args[0].equals("--help")){
                    usage();
                    return;
                }

                int idx = args[0].indexOf(':');
                if(idx > 0){
                    port = Integer.parseInt(args[0].substring(idx+1));
                    addr = InetAddress.getByName(args[0].substring(0,idx));
                }
                else {
                    addr = InetAddress.getByName(args[0]);
                }
            }
            else {
                addr = InetAddress.getByName("localhost");
            }

            System.out.println("Using ADDR:PORT " + addr.getHostAddress() + ":" + port);

            con = new TCPMasterConnection(addr);
            con.setPort(port);
            con.connect();

        } catch (Exception ex) {
            System.out.println("Connect: "+ex.getMessage());
        }

        // register for Golgi
        golgiSetup();

        // all set up to receive Modbus data reads from a helmet now complete

        /*
         * uncomment if you want to periodically poll the Modbus connection and send updates
         */
//        while(true){
//            try {
//                // this example will read "Max. Average Voltage Vph-n" which is a float from the Sentron
//                SentronData sd = readModbusRegister(131, 2);
//                float f = ByteBuffer.wrap(sd.getRegisterData()).order(ByteOrder.LITTLE_ENDIAN).getFloat();
//                sendNotification(String.valueOf(f));
//            }
//            catch(ModbusIOException ex){
//
//            }
//            catch(ModbusSlaveException ex){
//
//            }
//            catch(ModbusException ex){
//
//            }
//            catch(SentronReadException sre){
//
//            }
//        }
    }
}
