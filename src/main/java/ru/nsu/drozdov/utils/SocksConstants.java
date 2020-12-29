package ru.nsu.drozdov.utils;

public interface SocksConstants {
    int COMMAND_BUFFER_SIZE = 512;
    int DATA_BUFFER_SIZE = 10*65536;

    byte[] AUTH_MESS_ACCEPT = {(byte)0x05, (byte)0x00};
    byte[] AUTH_MESS_REFUSE = {(byte)0x05, (byte)0xFF};


    byte[] VOID_IP = {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};
    byte[] VOID_PORT = {(byte)0x00, (byte)0x00};

    byte SC_CONNECT = 0x01;
    byte SC_BIND = 0x02;
    byte SC_UDP = 0x03;

    byte ATYPE_IPV4 = 0x01;
    byte ATYPE_DOMAINNAME = 0x03;
    byte ATYPE_IPV6 = 0x04;

    byte HOST_UNREACHABLE = 0x04;
}
