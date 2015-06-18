var APP_INSTANCE_ID = 'DAQRI_WEB';
var gto = {"EXPIRY":60};
// register with Golgi server
function registerWithGolgi(){
    GolgiNet.register(function(err){
        if(err != undefined){
            console.log("Failed to register");
            alert("Web Application failed to register with Golgi");
        }
        else{
            console.log("Successfully registered");
        }
    });
}

function setGolgiCredentials(){
    GolgiNet.setCredentials(DEV_KEY, 
                            APP_KEY, 
                            APP_INSTANCE_ID);
    console.log('Set Golgi Credentials to ' + APP_INSTANCE_ID);
    registerWithGolgi();
}

function init()
{
    // initialise the lib and net
    GolgiLib.init();
    GolgiNet.init();
    DaqriGolgi.ServiceInit();

    DaqriGolgi.DaqriSvc.registerNotifyHandler(function(resultHandler,dn){
        console.log("Received a notification");
        console.log("Notification is: " + dn.getNot());
        alert("Inbound Notification: " + dn.getNot());
    });

    setGolgiCredentials();
}

function request_data(index, count)
{
    var sr = DaqriGolgi.SentronRequest();
    sr.setIndex(index);
    sr.setNumberRegisters(count);
    DaqriGolgi.DaqriSvc.read({
        success: function(sentronData){
            console.log("Received Sentron data read");
            var data = sentronData.getRegisterData();
            for(var i = 0, n = data.length; i < n; i++){
                console.log("Data is: " + data[i].toString(16));
            }
        },
        failWithGolgiException: function(golgiException){
            console.log("Failed to retrieve data from ModbusServer: " + golgiException.getErrText());
        },
        failWithSre: function(sre){
            console.log("Failed to retrieve data from ModbusServer: " + sre.getError());
        }},
        "DAQRI_SERVER",
        gto,
        sr);
}

function read_data_button()
{
    request_data(131,2);
}

init();
