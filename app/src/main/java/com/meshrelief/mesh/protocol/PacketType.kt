package com.meshrelief.mesh.protocol

enum class PacketType {
    TEXT_MESSAGE,
    GROUP_MESSAGE,
    SOS_ALERT,
    SOS_CANCEL,
    DEVICE_STATUS,
    CAMP_UPDATE,
    EVACUATION_ROUTE,
    BULLETIN,
    HEADCOUNT_PING,
    HEADCOUNT_RESPONSE,
    PEER_VERIFY,
    PEER_FLAG,
    ACK
}