package de.fhws.indoor.libsmartphonesensors.helpers;

import no.nordicsemi.android.ble.data.Data;

public class BleDataStream {
    private final Data _data;
    private int _pos;

    public BleDataStream(Data data) {
        _data = data;
        _pos = 0;
    }

    public int position() {
        return _pos;
    }

    public int size() {
        return _data.size();
    }

    public boolean eof() {
        return _pos >= _data.size();
    }

    public byte readByte() {
        return _data.getByte(_pos++);
    }

    private int readInteger(final int formatType) {
        int result = _data.getIntValue(formatType, _pos);

        switch (formatType) {
            case Data.FORMAT_SINT8:
            case Data.FORMAT_UINT8:
                _pos++;
                break;
            case Data.FORMAT_SINT16:
            case Data.FORMAT_UINT16:
                _pos += 2;
                break;
            case Data.FORMAT_SINT24:
            case Data.FORMAT_UINT24:
                _pos += 3;
                break;
            case Data.FORMAT_SINT32:
            case Data.FORMAT_UINT32:
                _pos += 4;
                break;
        }

        return result;
    }

    public int readUInt16() {
        return readInteger(Data.FORMAT_UINT16);
    }

    public int readUInt32() {
        return readInteger(Data.FORMAT_UINT32);
    }

    public float readFloat() {
        float result = _data.getFloatValue(Data.FORMAT_FLOAT, _pos);
        _pos += 4;
        return result;
    }
}