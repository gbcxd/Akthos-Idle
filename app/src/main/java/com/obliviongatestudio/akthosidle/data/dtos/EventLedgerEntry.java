package com.obliviongatestudio.akthosidle.data.dtos;

public class EventLedgerEntry {
    public long id;
    public String type;       // e.g., "startJob", "finishJob", "fight"
    public String payloadJson;
    public long ts;
}
