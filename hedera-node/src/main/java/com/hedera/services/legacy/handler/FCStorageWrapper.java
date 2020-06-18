package com.hedera.services.legacy.handler;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.state.merkle.BlobPath;
import com.hedera.services.state.merkle.OptionalBlob;
import com.hedera.services.legacy.exception.StorageKeyNotFoundException;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FCStorageWrapper {
  private static final Logger log = LogManager.getLogger(FCStorageWrapper.class);
  private FCMap<BlobPath, OptionalBlob> storageMap;

  public FCStorageWrapper() {
  }

  public FCStorageWrapper(FCMap<BlobPath, OptionalBlob> storageMap) {
    this.storageMap = storageMap;
  }


  public void fileCreate(String path, byte[] content, long createTimeSec, int createTimeNs,
      long expireTimeSec, byte[] metadata) {
    BlobPath sKey = new BlobPath(path);
    OptionalBlob sVal = new OptionalBlob(content);
    storageMap.put(sKey, sVal);
  }

  public byte[] fileRead(String path) {
    BlobPath sKey;
    try {
      sKey = validateStorageKey(path);
    } catch (StorageKeyNotFoundException e) {
      return new byte[0];
    }
    return storageMap.get(sKey).getData();
  }

  public boolean fileExists(String path) {
    BlobPath sKey;
    try {
      sKey = validateStorageKey(path);
      return storageMap.containsKey(sKey);
    } catch (StorageKeyNotFoundException e) {
    }
    return false;
  }

  public long getSize(String path) {
    BlobPath sKey = new BlobPath(path);
    if (storageMap.containsKey(sKey)) {
      return storageMap.get(sKey).getData().length;
    } else {
      return 0l;
    }
  }

  public void fileUpdate(String path, byte[] content, long modifyTimeSec,
      int modifyTimeNs, long expireTimeSec) {
    byte[] existingContent = fileRead(path);
    byte[] appendedContent = ArrayUtils.addAll(existingContent, content);
    fileCreate(path, appendedContent, modifyTimeSec, modifyTimeNs, expireTimeSec, null);
  }

  private BlobPath validateStorageKey(String path) throws StorageKeyNotFoundException {
    BlobPath sKey = new BlobPath(path);
    if (!storageMap.containsKey(sKey)) {
      throw new StorageKeyNotFoundException("Destination file does not exist: '" + path + "'");
    }
    return sKey;
  }

  public void delete(String path, long modifyTimeSec, int modifyTimeNs)
      throws StorageKeyNotFoundException {
    BlobPath sKey = validateStorageKey(path);
    storageMap.remove(sKey);
  }
}
