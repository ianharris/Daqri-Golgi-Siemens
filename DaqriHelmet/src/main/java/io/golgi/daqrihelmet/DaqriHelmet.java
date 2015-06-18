package io.golgi.daqrihelmet;

import com.openmindnetworks.golgi.api.*;
import io.golgi.daqri.gen.*;

/**
 * Created by ianh on 15/06/2015.
 *
 * The DaqriHelmet process is intended to run on a Daqri helmet unit. The DaqriHelmet can request data from the Siemens
 * Sentron PAC3200 by sending a request via Golgi to the ModbusClient. The ModbusClient will respond with the values
 * read in a byte array as well as the first index read.
 */
public class DaqriHelmet {

    private static boolean golgiRegistered = false;
    private static String uuid = "DAQRI_HELMET";
    private static GolgiTransportOptions gto;

    private static DaqriService.read.ResultReceiver readResultReceiver = new DaqriService.read.ResultReceiver() {
        @Override
        public void failure(GolgiException ex) {
            System.out.println("DaqriService.read.ResultReceiver: " + ex.getMessage());
        }

        @Override
        public void failure(SentronReadException sre){
            System.out.println("DaqriService.read.ResultReceiver: " + sre.getMessage());
        }

        @Override
        public void success(SentronData result) {
            byte[] data = result.getRegisterData();
            System.out.println("Begin values");

            for(int i = 0;
                i < data.length;
                i++){
                System.out.println(String.format("0x%02X",data[i]));
            }
            System.out.println("End values");
        }
    };

    private static DaqriService.notify.RequestReceiver notifyRequestReceiver = new DaqriService.notify.RequestReceiver() {
        @Override
        public void receiveFrom(DaqriService.notify.ResultSender resultSender, DaqriNotification dn) {
            System.out.println("Received notification: " + dn.getNot());
            resultSender.success();
        }
    };

    private static void golgiSetup(){

        // Static reference to load the class
        Class<GolgiAPI> apiRef = GolgiAPI.class;
        // Host and port of Golgi Servers
        GolgiAPI.setAPIImpl(new GolgiAPINetworkImpl());

        /*
         * uncomment if you wish to receive notifies
         *
         * Note you'll also have to register see the end of this method
         */
        // DaqriService.notify.registerReceiver(notifyRequestReceiver);
        // register with id DAQRI_HELMET
        GolgiAPI.getInstance().register(GolgiKeys.DEV_KEY,
                                        GolgiKeys.APP_KEY,
                                        uuid,
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


        /*
         * uncomment if you want to register this helmet with the Daqri Server - i.e. the ModbusClient. This would allow
         * the server to send updates to the helmet without the need for the helmet to request them. This is a good
         * particularly useful for sending notifications for system failures or other critical events. A good example would
         * be a notification when the energy meter fails - most easily identified by the connection between ModbusClient and
         * the Siemens Sentron going down.
         *
         * Note you'll also have to register a notification receiver see above in this method
         */
//        DaqriRegister dr = new DaqriRegister();
//        dr.setUid(uuid);
//        DaqriService.register.sendTo(new DaqriService.register.ResultReceiver() {
//            @Override
//            public void failure(GolgiException ex) {
//                System.out.println("Successfully registered with the DAQRI_SERVER");
//            }
//
//            @Override
//            public void success() {
//                System.out.println("Failed to register with the DAQRI_SERVER");
//            }
//        },gto,"DAQRI_SERVER",dr);
    }

    public static void main(String [] args){

        gto = new GolgiTransportOptions();
        gto.setValidityPeriod(60);

        // register for Golgi
        golgiSetup();

        while(true){
            SentronRequest sr = new SentronRequest();
            sr.setIndex(131); // read "Max. Average Voltage Vph-n"
            sr.setNumberRegisters(2);

            DaqriService.read.sendTo(readResultReceiver,gto,"DAQRI_SERVER",sr);

            try{
                Thread.sleep(5000);
            }
            catch(InterruptedException ex){
                System.out.println("InterruptedException: " + ex.getMessage());
            }
        }
    }
}
