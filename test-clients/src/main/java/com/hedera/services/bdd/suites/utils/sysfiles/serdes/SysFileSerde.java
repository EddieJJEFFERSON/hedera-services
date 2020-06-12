package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import com.google.protobuf.InvalidProtocolBufferException;

public interface SysFileSerde<T> {
	T fromRawFile(byte[] bytes);
	byte[] toRawFile(T styledFile);
	String preferredFileName();
}
