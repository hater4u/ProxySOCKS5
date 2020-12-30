package ru.nsu.drozdov.utils;

public enum SocksStage {
    AUTH,
    CLIENT_COMM,
    CONNECTION_TO_SERVER,
    RELAY_READ_FROM_CLIENT,
    RELAY_READ_FROM_SERVER,
    RELAY_WRITE_TO_CLIENT,
}
