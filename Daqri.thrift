namespace java io.golgi.daqri.gen
namespace javascript DaqriGolgi

struct SentronRequest {
    1:required i32 index,
    2:required i32 numberRegisters
}

struct SentronData {
    1:required i32 index,
    2:required i32 numberRegisters,
    3:required data registerData
}

exception SentronReadException {
    1:required string error
}

struct DaqriNotification {
    1:required string not
}

struct DaqriRegister {
    1:required string uid
}

service Daqri {
    SentronData read(1:SentronRequest sr) throws(1:SentronReadException sre),
    void register(1:DaqriRegister dr),
    void notify(1:DaqriNotification dn)
}

